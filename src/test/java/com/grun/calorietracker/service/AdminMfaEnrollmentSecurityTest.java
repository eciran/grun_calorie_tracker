package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.security.*;
import com.grun.calorietracker.service.impl.AdminMfaServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminMfaEnrollmentSecurityTest {
    final UserRepository users = mock(UserRepository.class);
    final AdminMfaRecoveryCodeRepository recovery = mock(AdminMfaRecoveryCodeRepository.class);
    final RefreshTokenRepository refresh = mock(RefreshTokenRepository.class);
    final PasswordEncoder passwords = mock(PasswordEncoder.class);
    final TotpService totp = mock(TotpService.class);
    final AdminMfaSecretCipher cipher = mock(AdminMfaSecretCipher.class);
    final JwtUtil jwt = mock(JwtUtil.class);
    final AdminSessionService sessions = mock(AdminSessionService.class);
    final AdminMfaAccessPolicy policy = new AdminMfaAccessPolicy();
    final AdminMfaServiceImpl service = new AdminMfaServiceImpl(users, recovery, refresh, passwords,
            totp, cipher, jwt, mock(AdminAuditService.class), policy, sessions);

    @Test void ownerRequiresEnrollmentEvenWhenGeneralAdminPolicyIsOptional() {
        var owner = owner();
        assertTrue(service.requiresEnrollment(owner));
        assertThrows(IllegalArgumentException.class, () -> service.verifyLogin(owner, null));
        verifyNoInteractions(jwt, sessions);
    }

    @Test void enrollmentLoginIssuesOnlyShortLivedSetupResponse() {
        var owner = owner();
        when(jwt.generateAdminMfaEnrollmentTokenForUser(owner)).thenReturn("setup-only");
        var response = service.enrollmentLogin(owner);
        assertInstanceOf(com.grun.calorietracker.dto.AdminMfaEnrollmentAuthResponse.class, response);
        assertEquals(300L, response.getExpiresIn());
        assertNull(response.getRefreshToken());
        verifyNoInteractions(sessions);
    }

    @Test void enrolledOwnerCannotIssueAnotherSetupTokenOrDisableMfa() {
        var owner = owner();
        owner.setAdminMfaEnabled(true);
        when(users.findByEmailForUpdate(owner.getEmail())).thenReturn(Optional.of(owner));
        assertFalse(service.requiresEnrollment(owner));
        assertThrows(IllegalArgumentException.class, () -> service.enrollmentLogin(owner));
        assertThrows(IllegalArgumentException.class, () -> service.disable(owner.getEmail(), "123456", "cid"));
        verifyNoInteractions(jwt, totp, recovery, sessions);
    }

    @Test void passwordAloneCannotReplaceExistingMfa() {
        var owner = owner();
        owner.setAdminMfaEnabled(true);
        when(users.findByEmailForUpdate(owner.getEmail())).thenReturn(Optional.of(owner));
        assertThrows(IllegalArgumentException.class, () -> service.beginEnrollment(owner.getEmail(), "password", "cid"));
        verifyNoInteractions(passwords, totp, cipher, recovery);
        assertTrue(owner.getAdminMfaEnabled());
    }

    @Test void delegatedAdminsFollowConfiguredPolicyWhileStandardUsersDoNotEnroll() {
        var user = owner();
        user.setRole(UserRole.ADMIN_SUPPORT);
        assertFalse(service.requiresEnrollment(user));
        ReflectionTestUtils.setField(policy, "required", true);
        assertTrue(service.requiresEnrollment(user));
        user.setRole(UserRole.STANDARD);
        assertFalse(service.requiresEnrollment(user));
    }

    @Test void expiredEnrollmentCannotEnableMfa() {
        var owner = pending();
        owner.setAdminMfaEnrollmentStartedAt(Instant.now().minusSeconds(301));
        assertThrows(IllegalArgumentException.class, () -> service.verifyEnrollment(owner.getEmail(), "123456", "cid"));
        assertFalse(owner.getAdminMfaEnabled());
        verifyNoInteractions(totp, sessions);
    }

    @Test void verificationReplayCannotRegenerateRecoveryCodes() {
        var owner = pending();
        owner.setAdminMfaEnabled(true);
        assertThrows(IllegalArgumentException.class, () -> service.verifyEnrollment(owner.getEmail(), "123456", "cid"));
        verifyNoInteractions(recovery, totp, sessions);
    }

    @Test void successfulEnrollmentRevokesEveryExistingAdminSessionAndCreatesRecoveryCodes() {
        var owner = pending();
        when(cipher.decrypt("encrypted")).thenReturn("secret");
        when(totp.verify("secret", "123456")).thenReturn(true);
        var response = service.verifyEnrollment(owner.getEmail(), "123456", "cid");
        assertTrue(response.enabled());
        assertEquals(8, response.recoveryCodes().size());
        assertTrue(owner.getAdminMfaEnabled());
        assertNotNull(owner.getAdminMfaVerifiedAt());
        verify(sessions).revokeAllForUser(owner);
        verify(refresh).findByUserAndRevokedAtIsNullAndUsedAtIsNull(owner);
        verify(recovery, times(8)).save(any());
        verifyNoInteractions(jwt);
    }

    @Test void invalidCodeDoesNotEnableMfaOrCreateRecoveryCodes() {
        var owner = pending();
        when(cipher.decrypt("encrypted")).thenReturn("secret");
        assertThrows(IllegalArgumentException.class, () -> service.verifyEnrollment(owner.getEmail(), "123456", "cid"));
        assertFalse(owner.getAdminMfaEnabled());
        verifyNoInteractions(recovery, sessions);
    }

    @Test void disabledOwnerCannotUsePendingEnrollment() {
        var owner = pending();
        owner.setAccountEnabled(false);
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                () -> service.verifyEnrollment(owner.getEmail(), "123456", "cid"));
        verifyNoInteractions(totp, sessions);
    }

    private UserEntity owner() {
        var user = new UserEntity();
        user.setId(42L);
        user.setEmail("owner@example.com");
        user.setRole(UserRole.OWNER);
        user.setAdminMfaEnabled(false);
        return user;
    }

    private UserEntity pending() {
        var owner = owner();
        owner.setAdminMfaSecretEncrypted("encrypted");
        owner.setAdminMfaEnrollmentStartedAt(Instant.now());
        when(users.findByEmailForUpdate(owner.getEmail())).thenReturn(Optional.of(owner));
        return owner;
    }
}
