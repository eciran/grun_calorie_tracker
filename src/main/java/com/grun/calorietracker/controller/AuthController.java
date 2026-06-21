// All comments are in English as requested in the project rules.
package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.AppleLoginRequestDto;
import com.grun.calorietracker.dto.AuthRequest;
import com.grun.calorietracker.dto.AuthResponse;
import com.grun.calorietracker.dto.EmailVerificationConfirmRequestDto;
import com.grun.calorietracker.dto.EmailVerificationRequestDto;
import com.grun.calorietracker.dto.EmailVerificationResponseDto;
import com.grun.calorietracker.dto.GoogleLoginRequestDto;
import com.grun.calorietracker.dto.LogoutRequestDto;
import com.grun.calorietracker.dto.LogoutResponseDto;
import com.grun.calorietracker.dto.PasswordResetConfirmRequestDto;
import com.grun.calorietracker.dto.PasswordResetRequestDto;
import com.grun.calorietracker.dto.PasswordResetResponseDto;
import com.grun.calorietracker.dto.RefreshTokenRequestDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.service.EmailVerificationService;
import com.grun.calorietracker.service.FederatedAuthService;
import com.grun.calorietracker.service.PasswordResetService;
import com.grun.calorietracker.service.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration and login endpoints that issue JWT access tokens.")
public class AuthController {

    private final UserRepository userRepository;
    private final GoalRepository goalRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;
    private final RefreshTokenService refreshTokenService;
    private final FederatedAuthService federatedAuthService;


    @PostMapping("/register")
    @Operation(
            summary = "Register a new user",
            description = "Creates a standard user account, sends an email verification link, and returns session tokens so onboarding can continue."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User registered successfully."),
            @ApiResponse(
                    responseCode = "400",
                    description = "Email is already registered or request validation failed.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Too many requests from the same client.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))
            )
    })
    public ResponseEntity<?> register(@RequestBody @Valid AuthRequest request, HttpServletRequest httpRequest) {
        java.util.Optional<UserEntity> existingUser = userRepository.findByEmail(request.getEmail());
        if (existingUser.isPresent()) {
            return resumeIncompleteRegistrationOrReject(existingUser.get(), request, httpRequest);
        }
        return createNewUserRegistration(request);
    }

    private ResponseEntity<?> resumeIncompleteRegistrationOrReject(
            UserEntity existingUser,
            AuthRequest request,
            HttpServletRequest httpRequest
    ) {
        if (canResumeIncompleteRegistration(existingUser, request.getPassword())) {
            resendVerificationIfNeeded(existingUser);
            return ResponseEntity.ok(buildAuthResponse(existingUser, "Registration resumed. Continue onboarding."));
        }

        return ResponseEntity.badRequest().body(new ApiErrorResponseDto(
                LocalDateTime.now(),
                400,
                "Validation error",
                "Email already registered",
                httpRequest.getRequestURI()
        ));
    }

    private ResponseEntity<AuthResponse> createNewUserRegistration(AuthRequest request) {
        UserEntity newUser = new UserEntity();
        newUser.setEmail(request.getEmail());
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        newUser.setRole(UserRole.STANDARD);
        newUser.setEmailVerified(false);
        newUser.setPasswordSet(true);

        UserEntity savedUser = userRepository.save(newUser);
        emailVerificationService.createVerificationTokenForUser(savedUser);

        return ResponseEntity.ok(buildAuthResponse(savedUser,
                "User registered successfully. Email verification can be completed later."));
    }

    private boolean canResumeIncompleteRegistration(UserEntity user, String rawPassword) {
        if (!Boolean.TRUE.equals(user.getAccountEnabled()) || Boolean.TRUE.equals(user.getAccountLocked())) {
            return false;
        }
        if (user.getPassword() == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            return false;
        }
        return !isOnboardingComplete(user);
    }

    private boolean isOnboardingComplete(UserEntity user) {
        return isProfileComplete(user) && goalRepository.findByUser(user).isPresent();
    }

    private boolean isProfileComplete(UserEntity user) {
        return user.getAge() != null
                && user.getGender() != null
                && user.getHeight() != null
                && user.getWeight() != null
                && user.getMarketRegion() != null
                && user.getPreferredLanguage() != null
                && user.getTimeZone() != null;
    }

    private void resendVerificationIfNeeded(UserEntity user) {
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return;
        }
        EmailVerificationRequestDto request = new EmailVerificationRequestDto();
        request.setEmail(user.getEmail());
        emailVerificationService.resendVerification(request);
    }

    private AuthResponse buildAuthResponse(UserEntity user, String message) {
        String token = jwtUtil.generateToken(user.getEmail());
        String refreshToken = refreshTokenService.createRefreshToken(user);
        return new AuthResponse(token, refreshToken, "Bearer", jwtUtil.getExpirationSeconds(), message);
    }

    @PostMapping("/login")
    @Operation(
            summary = "Login with email and password",
            description = "Authenticates an existing user and returns a short-lived JWT access token plus a long-lived refresh token for mobile sessions."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful."),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid email or password.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Too many requests from the same client.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))
            )
    })
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        String token = jwtUtil.generateToken(user.getEmail());
        String refreshToken = refreshTokenService.createRefreshToken(user);
        return ResponseEntity.ok(new AuthResponse(token, refreshToken, "Bearer", jwtUtil.getExpirationSeconds(), "Login successful"));
    }

    @PostMapping("/google")
    @Operation(
            summary = "Login or register with Google",
            description = "Verifies a Google ID token obtained by the mobile client, links the Google subject to a GRun account, and returns GRun access and refresh tokens."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Google login successful."),
            @ApiResponse(responseCode = "400", description = "Request validation failed or Google login is not configured.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Google ID token is invalid or unverified.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests from the same client.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AuthResponse> googleLogin(@RequestBody @Valid GoogleLoginRequestDto request) {
        return ResponseEntity.ok(federatedAuthService.loginWithGoogle(request.getIdToken()));
    }

    @PostMapping("/apple")
    @Operation(
            summary = "Login or register with Apple",
            description = "Verifies an Apple identity token and nonce received from the client, links the Apple subject to a GRun account, and returns GRun access and refresh tokens."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Apple login successful."),
            @ApiResponse(responseCode = "400", description = "Request validation failed or Apple login is not configured.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Apple identity token, nonce, or account identity is invalid.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests from the same client.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AuthResponse> appleLogin(@RequestBody @Valid AppleLoginRequestDto request) {
        return ResponseEntity.ok(federatedAuthService.loginWithApple(request.getIdToken(), request.getNonce()));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh access token",
            description = "Rotates a valid refresh token and returns a new access token plus a new refresh token."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully."),
            @ApiResponse(
                    responseCode = "400",
                    description = "Refresh token is invalid, expired, already used, revoked, or request validation failed.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Too many requests from the same client.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))
            )
    })
    public ResponseEntity<AuthResponse> refresh(@RequestBody @Valid RefreshTokenRequestDto request) {
        return ResponseEntity.ok(refreshTokenService.refreshAccessToken(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Logout current device session",
            description = "Revokes the submitted refresh token. The current access token naturally expires by its short TTL."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logout successful."),
            @ApiResponse(
                    responseCode = "400",
                    description = "Refresh token is invalid, expired, already used, revoked, or request validation failed.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))
            )
    })
    public ResponseEntity<LogoutResponseDto> logout(@RequestBody @Valid LogoutRequestDto request) {
        refreshTokenService.revokeRefreshToken(request.getRefreshToken());
        return ResponseEntity.ok(new LogoutResponseDto("Logout successful"));
    }

    @PostMapping("/email-verification/resend")
    @Operation(
            summary = "Resend email verification",
            description = "Creates a new email verification token for an unverified account and sends it through the configured mail sender. The response is generic to avoid revealing registered emails."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email verification resend request accepted."),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Too many requests from the same client.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))
            )
    })
    public ResponseEntity<EmailVerificationResponseDto> resendEmailVerification(
            @RequestBody @Valid EmailVerificationRequestDto request
    ) {
        return ResponseEntity.ok(emailVerificationService.resendVerification(request));
    }

    @PostMapping("/email-verification/confirm")
    @Operation(
            summary = "Confirm email verification",
            description = "Validates the email verification token and marks the account email as verified."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email verification completed."),
            @ApiResponse(
                    responseCode = "400",
                    description = "Token is invalid, expired, already used, or request validation failed.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))
            )
    })
    public ResponseEntity<EmailVerificationResponseDto> confirmEmailVerification(
            @RequestBody @Valid EmailVerificationConfirmRequestDto request
    ) {
        return ResponseEntity.ok(emailVerificationService.confirmVerification(request));
    }

    @PostMapping("/password-reset/request")
    @Operation(
            summary = "Request password reset",
            description = "Creates a short-lived password reset token and sends it through the configured mail sender. The response is generic to avoid revealing registered emails."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset request accepted."),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Too many requests from the same client.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))
            )
    })
    public ResponseEntity<PasswordResetResponseDto> requestPasswordReset(
            @RequestBody @Valid PasswordResetRequestDto request
    ) {
        return ResponseEntity.ok(passwordResetService.requestPasswordReset(request));
    }

    @PostMapping("/password-reset/confirm")
    @Operation(
            summary = "Confirm password reset",
            description = "Validates the password reset token and updates the account password."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset completed."),
            @ApiResponse(
                    responseCode = "400",
                    description = "Token is invalid, expired, already used, or request validation failed.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))
            )
    })
    public ResponseEntity<PasswordResetResponseDto> confirmPasswordReset(
            @RequestBody @Valid PasswordResetConfirmRequestDto request
    ) {
        return ResponseEntity.ok(passwordResetService.confirmPasswordReset(request));
    }
}
