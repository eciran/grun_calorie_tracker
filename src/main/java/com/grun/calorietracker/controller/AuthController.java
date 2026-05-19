// All comments are in English as requested in the project rules.
package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AuthRequest;
import com.grun.calorietracker.dto.AuthResponse;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.EmailVerificationConfirmRequestDto;
import com.grun.calorietracker.dto.EmailVerificationRequestDto;
import com.grun.calorietracker.dto.EmailVerificationResponseDto;
import com.grun.calorietracker.dto.LogoutRequestDto;
import com.grun.calorietracker.dto.LogoutResponseDto;
import com.grun.calorietracker.dto.PasswordResetConfirmRequestDto;
import com.grun.calorietracker.dto.PasswordResetRequestDto;
import com.grun.calorietracker.dto.PasswordResetResponseDto;
import com.grun.calorietracker.dto.RefreshTokenRequestDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.service.EmailVerificationService;
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
@RequestMapping({"/api/auth", "/api/v1/auth"})
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration and login endpoints that issue JWT access tokens.")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;
    private final RefreshTokenService refreshTokenService;


    @PostMapping("/register")
    @Operation(
            summary = "Register a new user",
            description = "Creates a standard user account, marks the email as unverified, and sends an email verification link."
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
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body(new ApiErrorResponseDto(
                    LocalDateTime.now(),
                    400,
                    "Validation error",
                    "Email already registered",
                    httpRequest.getRequestURI()
            ));
        }

        UserEntity newUser = new UserEntity();
        newUser.setEmail(request.getEmail());
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        newUser.setRole(UserRole.STANDARD);
        newUser.setEmailVerified(false);

        UserEntity savedUser = userRepository.save(newUser);
        emailVerificationService.createVerificationTokenForUser(savedUser);

        return ResponseEntity.ok(new AuthResponse(null, "User registered successfully. Please verify your email address."));
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

        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalArgumentException("Email address is not verified");
        }

        String token = jwtUtil.generateToken(user.getEmail());
        String refreshToken = refreshTokenService.createRefreshToken(user);
        return ResponseEntity.ok(new AuthResponse(token, refreshToken, "Bearer", jwtUtil.getExpirationSeconds(), "Login successful"));
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
