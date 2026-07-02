package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.AppleLoginRequestDto;
import com.grun.calorietracker.dto.AuthRequest;
import com.grun.calorietracker.dto.EmailVerificationConfirmRequestDto;
import com.grun.calorietracker.dto.EmailVerificationRequestDto;
import com.grun.calorietracker.dto.EmailVerificationResponseDto;
import com.grun.calorietracker.dto.LogoutRequestDto;
import com.grun.calorietracker.dto.LogoutResponseDto;
import com.grun.calorietracker.dto.GoogleLoginRequestDto;
import com.grun.calorietracker.dto.PasswordResetConfirmRequestDto;
import com.grun.calorietracker.dto.PasswordResetRequestDto;
import com.grun.calorietracker.dto.PasswordResetResponseDto;
import com.grun.calorietracker.dto.RefreshTokenRequestDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.EmailVerificationService;
import com.grun.calorietracker.service.FederatedAuthService;
import com.grun.calorietracker.service.PasswordResetService;
import com.grun.calorietracker.service.RefreshTokenService;
import com.grun.calorietracker.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private GoalRepository goalRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private PasswordResetService passwordResetService;

    @MockBean
    private EmailVerificationService emailVerificationService;

    @MockBean
    private RefreshTokenService refreshTokenService;

    @MockBean
    private FederatedAuthService federatedAuthService;

    @MockBean
    private AuthenticationManager authenticationManager;

    private UserEntity sampleUser;
    private UserProfileDto sampleUserProfileDto;

    @BeforeEach
    void setUp() {
        sampleUser = new UserEntity();
        sampleUser.setId(1L);
        sampleUser.setName("Test User");
        sampleUser.setEmail("testuser@example.com");
        sampleUser.setPassword("password");
        sampleUser.setRole(UserRole.STANDARD);
        sampleUser.setEmailVerified(true);
        sampleUser.setAge(30);
        sampleUser.setGender("MALE");
        sampleUser.setHeight(175.0);
        sampleUser.setWeight(70.0);

        sampleUserProfileDto = new UserProfileDto();
        sampleUserProfileDto.setId(1L);
        sampleUserProfileDto.setName("Test User");
        sampleUserProfileDto.setEmail("testuser@example.com");
        sampleUserProfileDto.setAge(30);
        sampleUserProfileDto.setGender("MALE");
        sampleUserProfileDto.setHeight(175.0);
        sampleUserProfileDto.setWeight(70.0);
    }

    @Test
    void testRegisterUser() throws Exception {
        AuthRequest request = new AuthRequest();
        request.setEmail("testuser@example.com");
        request.setPassword("Password1!");

        when(userRepository.findByEmail("testuser@example.com")).thenReturn(java.util.Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenReturn(sampleUser);
        when(refreshTokenService.createRefreshToken(any(UserEntity.class))).thenReturn("register-refresh-token");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").value("register-refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.message").value("User registered successfully. Email verification can be completed later."));

        verify(userRepository).save(any(UserEntity.class));
        verify(emailVerificationService).createVerificationTokenForUser(any(UserEntity.class));
    }

    @Test
    void register_whenPasswordContainsHyphenSpecialCharacter_acceptsPassword() throws Exception {
        AuthRequest request = new AuthRequest();
        request.setEmail("hyphen@example.com");
        request.setPassword("Pixel123--!");

        UserEntity savedUser = new UserEntity();
        savedUser.setId(2L);
        savedUser.setEmail("hyphen@example.com");
        savedUser.setRole(UserRole.STANDARD);
        savedUser.setEmailVerified(false);

        when(userRepository.findByEmail("hyphen@example.com")).thenReturn(java.util.Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
        when(refreshTokenService.createRefreshToken(any(UserEntity.class))).thenReturn("register-refresh-token");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").value("register-refresh-token"));
    }
    @Test
    void register_whenEmailAlreadyExists_returnsStandardErrorResponse() throws Exception {
        AuthRequest request = new AuthRequest();
        request.setEmail("testuser@example.com");
        request.setPassword("Password1!");
        sampleUser.setPassword(passwordEncoder.encode("DifferentPass1!"));

        when(userRepository.findByEmail("testuser@example.com")).thenReturn(java.util.Optional.of(sampleUser));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation error"))
                .andExpect(jsonPath("$.message").value("Email already registered"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));
    }

    @Test
    void openApi_registerBadRequest_usesErrorResponseSchema() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.responses['400'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponseDto"));
    }

    @Test
    void registerValidationError_whenTurkishLanguageRequested_returnsLocalizedErrorCategory() throws Exception {
        AuthRequest request = new AuthRequest();
        request.setEmail("invalid-email");
        request.setPassword("Password1!");

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Accept-Language", "tr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Dogrulama hatasi"))
                .andExpect(jsonPath("$.message").value("email: Email gecerli bir email adresi olmalidir"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));
    }

    @Test
    void requestPasswordReset_returnsGenericAcceptedResponse() throws Exception {
        PasswordResetRequestDto request = new PasswordResetRequestDto();
        request.setEmail("testuser@example.com");

        when(passwordResetService.requestPasswordReset(any(PasswordResetRequestDto.class)))
                .thenReturn(new PasswordResetResponseDto("If the email exists, a password reset link has been sent.", 60L));

        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If the email exists, a password reset link has been sent."))
                .andExpect(jsonPath("$.retryAfterSeconds").value(60));
    }

    @Test
    void resendEmailVerification_returnsGenericAcceptedResponse() throws Exception {
        EmailVerificationRequestDto request = new EmailVerificationRequestDto();
        request.setEmail("testuser@example.com");

        when(emailVerificationService.resendVerification(any(EmailVerificationRequestDto.class)))
                .thenReturn(new EmailVerificationResponseDto("If the email exists, a verification link has been sent."));

        mockMvc.perform(post("/api/v1/auth/email-verification/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If the email exists, a verification link has been sent."));
    }

    @Test
    void confirmEmailVerification_returnsSuccessResponse() throws Exception {
        EmailVerificationConfirmRequestDto request = new EmailVerificationConfirmRequestDto();
        request.setToken("raw-token");

        when(emailVerificationService.confirmVerification(any(EmailVerificationConfirmRequestDto.class)))
                .thenReturn(new EmailVerificationResponseDto("Email has been verified successfully."));

        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email has been verified successfully."));
    }

    @Test
    void login_whenEmailIsNotVerified_returnsSessionAndAllowsReminderFlow() throws Exception {
        AuthRequest request = new AuthRequest();
        request.setEmail("testuser@example.com");
        request.setPassword("Password1!");
        sampleUser.setEmailVerified(false);

        when(userRepository.findByEmail("testuser@example.com")).thenReturn(java.util.Optional.of(sampleUser));
        when(refreshTokenService.createRefreshToken(sampleUser)).thenReturn("refresh-token");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.message").value("Login successful"));
    }

    @Test
    void confirmPasswordReset_returnsSuccessResponse() throws Exception {
        PasswordResetConfirmRequestDto request = new PasswordResetConfirmRequestDto();
        request.setToken("raw-token");
        request.setNewPassword("NewStrongPass1!");

        when(passwordResetService.confirmPasswordReset(any(PasswordResetConfirmRequestDto.class)))
                .thenReturn(new PasswordResetResponseDto("Password has been reset successfully."));

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been reset successfully."));
    }

    @Test
    void refresh_returnsNewAccessAndRefreshTokens() throws Exception {
        RefreshTokenRequestDto request = new RefreshTokenRequestDto();
        request.setRefreshToken("raw-refresh-token");

        when(refreshTokenService.refreshAccessToken("raw-refresh-token"))
                .thenReturn(new com.grun.calorietracker.dto.AuthResponse(
                        "new-access-token",
                        "new-refresh-token",
                        "Bearer",
                        900L,
                        "Token refreshed successfully"
                ));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.message").value("Token refreshed successfully"));
    }

    @Test
    void googleLogin_returnsGrunSessionTokens() throws Exception {
        GoogleLoginRequestDto request = new GoogleLoginRequestDto();
        request.setIdToken("google-id-token");
        when(federatedAuthService.loginWithGoogle("google-id-token"))
                .thenReturn(new com.grun.calorietracker.dto.AuthResponse(
                        "google-access-token",
                        "google-refresh-token",
                        "Bearer",
                        900L,
                        "Google login successful"
                ));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("google-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("google-refresh-token"))
                .andExpect(jsonPath("$.message").value("Google login successful"));
    }

    @Test
    void appleLogin_returnsGrunSessionTokens() throws Exception {
        AppleLoginRequestDto request = new AppleLoginRequestDto();
        request.setIdToken("apple-id-token");
        request.setNonce("apple-nonce");
        when(federatedAuthService.loginWithApple("apple-id-token", "apple-nonce"))
                .thenReturn(new com.grun.calorietracker.dto.AuthResponse(
                        "apple-access-token",
                        "apple-refresh-token",
                        "Bearer",
                        900L,
                        "Apple login successful"
                ));

        mockMvc.perform(post("/api/v1/auth/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("apple-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("apple-refresh-token"))
                .andExpect(jsonPath("$.message").value("Apple login successful"));
    }

    @Test
    void refresh_v1Path_returnsNewAccessAndRefreshTokens() throws Exception {
        RefreshTokenRequestDto request = new RefreshTokenRequestDto();
        request.setRefreshToken("raw-refresh-token-v1");

        when(refreshTokenService.refreshAccessToken("raw-refresh-token-v1"))
                .thenReturn(new com.grun.calorietracker.dto.AuthResponse(
                        "new-access-token-v1",
                        "new-refresh-token-v1",
                        "Bearer",
                        900L,
                        "Token refreshed successfully"
                ));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-access-token-v1"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token-v1"));
    }

    @Test
    void logout_revokesRefreshToken() throws Exception {
        LogoutRequestDto request = new LogoutRequestDto();
        request.setRefreshToken("raw-refresh-token");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"));

        verify(refreshTokenService).revokeRefreshToken("raw-refresh-token");
    }

    @Test
    void openApi_productBarcodeErrors_useEndpointSpecificExamples() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/products/barcode/{barcode}'].get.responses['401'].content['application/json'].example.status")
                        .value(401))
                .andExpect(jsonPath("$.paths['/api/v1/products/barcode/{barcode}'].get.responses['401'].content['application/json'].example.path")
                        .value("/api/v1/products/barcode/{barcode}"))
                .andExpect(jsonPath("$.paths['/api/v1/products/barcode/{barcode}'].get.responses['401'].content['application/json'].example.message")
                        .value("JWT token is missing or invalid."))
                .andExpect(jsonPath("$.paths['/api/v1/products/barcode/{barcode}'].get.responses['404'].content['application/json'].example.status")
                        .value(404))
                .andExpect(jsonPath("$.paths['/api/v1/products/barcode/{barcode}'].get.responses['404'].content['application/json'].example.path")
                        .value("/api/v1/products/barcode/{barcode}"))
                .andExpect(jsonPath("$.paths['/api/v1/products/barcode/{barcode}'].get.responses['404'].content['application/json'].example.message")
                        .value("Product could not be found locally or from the configured external source."));
    }

    @Test
    void openApi_v1AuthPath_isDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.responses['429'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponseDto"));
    }

    @Test
    void openApi_loginDoesNotRequireEmailVerification() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['403']").doesNotExist());
    }

    @Test
    void register_whenExistingAccountHasIncompleteOnboardingAndPasswordMatches_resumesRegistration() throws Exception {
        AuthRequest request = new AuthRequest();
        request.setEmail("testuser@example.com");
        request.setPassword("Password1!");
        sampleUser.setPassword(passwordEncoder.encode("Password1!"));
        sampleUser.setEmailVerified(false);
        sampleUser.setMarketRegion(null);

        when(userRepository.findByEmail("testuser@example.com")).thenReturn(java.util.Optional.of(sampleUser));
        when(goalRepository.findByUser(sampleUser)).thenReturn(java.util.Optional.empty());
        when(refreshTokenService.createRefreshToken(sampleUser)).thenReturn("resume-refresh-token");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").value("resume-refresh-token"))
                .andExpect(jsonPath("$.message").value("Registration resumed. Continue onboarding."));

        verify(emailVerificationService).resendVerification(any(EmailVerificationRequestDto.class));
    }
}

