package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.service.AdminMfaService;
import com.grun.calorietracker.service.AdminSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "grun.rate-limit.enabled=false")
@AutoConfigureMockMvc
class AdminMfaEnrollmentHttpSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @MockBean UserRepository users;
    @MockBean AuthenticationManager authenticationManager;
    @MockBean AdminMfaService mfa;
    @MockBean AdminSessionService sessions;
    UserEntity owner;
    String token;

    @BeforeEach void setup() {
        owner = new UserEntity();
        owner.setId(42L);
        owner.setEmail("owner@example.com");
        owner.setPassword("encoded-password");
        owner.setRole(UserRole.OWNER);
        when(users.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(users.findAuthTokenVersionById(owner.getId())).thenAnswer(invocation -> Optional.of(owner.getAuthTokenVersion()));
        when(mfa.requiresEnrollment(owner)).thenReturn(true);
        token = jwt.generateAdminMfaEnrollmentToken(owner.getEmail());
        when(mfa.enrollmentLogin(owner)).thenReturn(new AdminMfaEnrollmentAuthResponse(token));
    }

    @Test void passwordLoginOnlyReturnsSetupAuthorizationWithoutSessionOrCookie() throws Exception {
        mvc.perform(post("/api/v1/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@example.com\",\"password\":\"StrongPass1!\"}"))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.mfaEnrollmentRequired").value(true))
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andExpect(jsonPath("$.adminSession").doesNotExist());
        verifyNoInteractions(sessions);
        verify(mfa, never()).verifyLogin(any(), any());
    }

    @Test void setupTokenCannotAccessAdminOrOrdinaryAuthenticatedApis() throws Exception {
        for (String path : List.of("/api/v1/admin/security/me", "/api/v1/users/me", "/api/v1/admin/security/mfa")) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/admin/security/mfa/disable").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(sessions);
        verify(mfa, never()).disable(anyString(), anyString(), any());
    }

    @Test void setupTokenCanOnlyBeginAndVerifyEnrollment() throws Exception {
        when(mfa.beginEnrollment(anyString(), anyString(), any())).thenReturn(new AdminMfaEnrollmentDto("SECRET", "otpauth://totp/test"));
        when(mfa.verifyEnrollment(anyString(), anyString(), any())).thenReturn(new AdminMfaVerificationDto(true, List.of("ABCD-EFGH-IJKL")));
        mvc.perform(post("/api/v1/auth/admin/mfa/enrollment").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"StrongPass1!\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.secret").value("SECRET"));
        mvc.perform(post("/api/v1/auth/admin/mfa/enrollment/verify").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
        verifyNoInteractions(sessions);
    }

    @Test void anonymousReauthenticationAndNormalSessionTokensCannotAuthorizeSetup() throws Exception {
        String reauth = jwt.generateAdminReauthenticationToken(owner.getEmail());
        String normal = jwt.generateAdminSessionToken(owner.getEmail(), "sid", Instant.now().plusSeconds(600));
        when(sessions.validateAndTouch("sid", owner.getEmail())).thenReturn(true);
        for (String proof : List.of("", reauth, normal)) {
            mvc.perform(post("/api/v1/auth/admin/mfa/enrollment").header("Authorization", "Bearer " + proof)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"StrongPass1!\"}"))
                    .andExpect(status().isForbidden());
        }
        verify(mfa, never()).beginEnrollment(anyString(), anyString(), any());
    }

    @Test void usedSetupAuthorizationIsRejectedAfterEnrollment() throws Exception {
        when(mfa.requiresEnrollment(owner)).thenReturn(false);
        mvc.perform(post("/api/v1/auth/admin/mfa/enrollment/verify").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isForbidden());
        verify(mfa, never()).verifyEnrollment(anyString(), anyString(), any());
    }

    @Test void setupAuthorizationIssuedBeforePasswordResetIsRejected() throws Exception {
        owner.setAuthTokenVersion(1);
        mvc.perform(post("/api/v1/auth/admin/mfa/enrollment/verify").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isForbidden());
        verify(mfa, never()).verifyEnrollment(anyString(), anyString(), any());
    }
}
