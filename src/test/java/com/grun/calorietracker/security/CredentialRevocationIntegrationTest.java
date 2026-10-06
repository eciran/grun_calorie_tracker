package com.grun.calorietracker.security;

import com.grun.calorietracker.dto.AccountPasswordRequestDto;
import com.grun.calorietracker.dto.PasswordResetConfirmRequestDto;
import com.grun.calorietracker.entity.PasswordResetTokenEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.PasswordResetTokenRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "grun.rate-limit.enabled=false")
@AutoConfigureMockMvc
@Transactional
class CredentialRevocationIntegrationTest {
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository resetTokens;
    @Autowired PasswordResetService reset;
    @Autowired AccountIdentityService identities;
    @Autowired RefreshTokenService refresh;
    @Autowired AdminSessionService adminSessions;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtUtil jwt;
    @Autowired MockMvc mvc;
    @MockBean PasswordResetMailSender resetMail;
    @MockBean MailDeliveryService mail;
    @MockBean AdminAuditService audit;
    @MockBean UserActivityService activity;

    @Test void passwordResetRevokesOldAccessAndRefreshButAllowsFreshLoginTokens() throws Exception {
        UserEntity user = user();
        String access = jwt.generateUserToken(user);
        String oldRefresh = refresh.createRefreshToken(user);
        resetPassword(user);

        assertThat(users.findAuthTokenVersionById(user.getId())).contains(1L);
        assertThat(jwt.isTokenValid(access, user.getEmail())).isFalse();
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> refresh.refreshAccessToken(oldRefresh)).isInstanceOf(IllegalArgumentException.class);
        assertThat(passwords.matches("NewStrongPass1!", user.getPassword())).isTrue();
        assertThat(jwt.isTokenValid(jwt.generateUserToken(user), user.getEmail())).isTrue();
        String newRefresh = refresh.createRefreshToken(user);
        assertThat(jwt.isTokenValid(refresh.refreshAccessToken(newRefresh).getToken(), user.getEmail())).isTrue();
    }

    @Test void accountPasswordChangeInvalidatesAccessAndRefreshTokens() {
        UserEntity user = user();
        String access = jwt.generateUserToken(user);
        String oldRefresh = refresh.createRefreshToken(user);
        AccountPasswordRequestDto request = new AccountPasswordRequestDto();
        request.setCurrentPassword("OldStrongPass1!");
        request.setNewPassword("NewStrongPass1!");
        identities.updatePassword(user.getEmail(), request);
        assertThat(jwt.isTokenValid(access, user.getEmail())).isFalse();
        assertThatThrownBy(() -> refresh.refreshAccessToken(oldRefresh)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void globalLogoutCannotBeUndoneBySavingStaleProfileSecurityState() {
        UserEntity user = user();
        String access = jwt.generateUserToken(user);
        refresh.revokeAllForUser(user);
        user.setAuthTokenVersion(0);
        user.setName("Updated profile");
        users.saveAndFlush(user);
        assertThat(users.findAuthTokenVersionById(user.getId())).contains(1L);
        assertThat(jwt.isTokenValid(access, user.getEmail())).isFalse();
        assertThat(jwt.isTokenValid(jwt.generateUserToken(user), user.getEmail())).isFalse();
    }

    @Test void passwordResetInvalidatesSetupReauthenticationAndAdminCookie() throws Exception {
        UserEntity user = user();
        user.setRole(UserRole.ADMIN_READ_ONLY);
        users.saveAndFlush(user);
        String setup = jwt.generateAdminMfaEnrollmentTokenForUser(user);
        String reauth = jwt.generateAdminReauthenticationToken(user.getEmail());
        var session = adminSessions.create(user);
        resetPassword(user);
        assertThat(jwt.isTokenValid(setup, user.getEmail())).isFalse();
        assertThat(jwt.isAdminReauthenticationTokenValid(reauth, user.getEmail())).isFalse();
        assertThat(jwt.isTokenValid(session.response().getToken(), user.getEmail())).isFalse();
        assertThatThrownBy(() -> adminSessions.restore(session.rawSessionToken()))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    }

    @Test void normalDeviceLogoutDoesNotSignOutOtherDevices() {
        UserEntity user = user();
        String access = jwt.generateUserToken(user);
        String first = refresh.createRefreshToken(user);
        String second = refresh.createRefreshToken(user);
        refresh.revokeRefreshToken(first);
        assertThat(jwt.isTokenValid(access, user.getEmail())).isTrue();
        assertThat(refresh.refreshAccessToken(second).getToken()).isNotBlank();
        assertThat(users.findAuthTokenVersionById(user.getId())).contains(0L);
    }

    @Test void replayBeforePasswordResetCannotInvalidateNewLogin() throws Exception {
        UserEntity user = user();
        String usedRefresh = refresh.createRefreshToken(user);
        refresh.refreshAccessToken(usedRefresh);
        resetPassword(user);
        String freshRefresh = refresh.createRefreshToken(users.findById(user.getId()).orElseThrow());
        assertThatThrownBy(() -> refresh.refreshAccessToken(usedRefresh)).isInstanceOf(IllegalArgumentException.class);
        assertThat(users.findAuthTokenVersionById(user.getId())).contains(1L);
        assertThat(jwt.isTokenValid(refresh.refreshAccessToken(freshRefresh).getToken(), user.getEmail())).isTrue();
    }

    @Test void adminCookieCannotRestoreAnOldCredentialGenerationEvenWithoutRevokedFlag() {
        UserEntity user = user();
        user.setRole(UserRole.ADMIN_READ_ONLY);
        users.saveAndFlush(user);
        var session = adminSessions.create(user);
        users.incrementAuthTokenVersion(user.getId());
        assertThatThrownBy(() -> adminSessions.restore(session.rawSessionToken()))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        assertThatThrownBy(() -> adminSessions.create(user))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void failedResetRollsBackPasswordAndSecurityVersionTogether() throws Exception {
        UserEntity user = user();
        user.setRole(UserRole.ADMIN_READ_ONLY);
        users.saveAndFlush(user);
        String access = jwt.generateUserToken(user);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Audit unavailable"))
                .when(audit).record(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        try {
            assertThatThrownBy(() -> resetPassword(user)).isInstanceOf(IllegalArgumentException.class);
            assertThat(users.findAuthTokenVersionById(user.getId())).contains(0L);
            UserEntity persisted = users.findById(user.getId()).orElseThrow();
            assertThat(passwords.matches("OldStrongPass1!", persisted.getPassword())).isTrue();
            assertThat(jwt.isTokenValid(access, user.getEmail())).isTrue();
        } finally {
            resetTokens.deleteAll(resetTokens.findByUserAndUsedAtIsNull(user));
            users.deleteById(user.getId());
        }
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setEmail("credential-revocation@example.com");
        user.setName("Security test");
        user.setRole(UserRole.STANDARD);
        user.setPassword(passwords.encode("OldStrongPass1!"));
        return users.saveAndFlush(user);
    }

    private void resetPassword(UserEntity user) throws Exception {
        String raw = "reset-proof";
        PasswordResetTokenEntity token = new PasswordResetTokenEntity();
        token.setUser(user);
        token.setTokenHash(Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8))));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        resetTokens.saveAndFlush(token);
        PasswordResetConfirmRequestDto request = new PasswordResetConfirmRequestDto();
        request.setToken(raw);
        request.setNewPassword("NewStrongPass1!");
        reset.confirmPasswordReset(request);
        users.flush();
    }
}
