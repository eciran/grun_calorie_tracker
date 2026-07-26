package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AccountPasswordRequestDto;
import com.grun.calorietracker.dto.LinkedIdentityDto;
import com.grun.calorietracker.dto.VerifiedGoogleIdentityDto;
import com.grun.calorietracker.entity.FederatedIdentityEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AccountLinkErrorCode;
import com.grun.calorietracker.enums.AccountSecurityEventType;
import com.grun.calorietracker.enums.AuthProvider;
import com.grun.calorietracker.exception.AccountLinkException;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.FederatedIdentityRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.AccountIdentityServiceImpl;
import com.grun.calorietracker.service.impl.AccountIdentityTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountIdentityServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private FederatedIdentityRepository federatedIdentityRepository;
    @Mock private GoogleIdTokenVerifierService googleVerifier;
    @Mock private AppleIdTokenVerifierService appleVerifier;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AccountIdentityTransactionService transactionService;
    @Mock private AccountSecurityAuditService auditService;

    private AccountIdentityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AccountIdentityServiceImpl(
                userRepository,
                federatedIdentityRepository,
                googleVerifier,
                appleVerifier,
                passwordEncoder,
                refreshTokenService,
                transactionService,
                auditService
        );
        ReflectionTestUtils.setField(service, "providerProofMaxAgeSeconds", 300L);
    }

    @Test
    void listLinkedIdentities_returnsProviderDtos() {
        FederatedIdentityEntity identity = identity(user(1L, "user@grun.app"), AuthProvider.GOOGLE);
        when(federatedIdentityRepository.findByUserEmailOrderByCreatedAtAsc("user@grun.app"))
                .thenReturn(List.of(identity));

        List<LinkedIdentityDto> result = service.listLinkedIdentities("user@grun.app");

        assertEquals(1, result.size());
        assertEquals(AuthProvider.GOOGLE, result.get(0).provider());
    }

    @Test
    void linkGoogle_withFreshProof_delegatesToLockedTransactionAndAuditsSuccess() {
        UserEntity user = user(1L, "user@grun.app");
        VerifiedGoogleIdentityDto verified = new VerifiedGoogleIdentityDto(
                "google-sub", "google@grun.app", "Google User", true, Instant.now());
        LinkedIdentityDto linked = new LinkedIdentityDto(AuthProvider.GOOGLE, "google@grun.app", LocalDateTime.now());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(googleVerifier.verify("google-token")).thenReturn(verified);
        when(transactionService.link(user.getEmail(), AuthProvider.GOOGLE,
                "google-sub", "google@grun.app", "opaque-token")).thenReturn(linked);

        LinkedIdentityDto result = service.linkGoogle(user.getEmail(), "google-token", "opaque-token");

        assertEquals(linked, result);
        verify(auditService).record(user.getId(), AccountSecurityEventType.ACCOUNT_LINK_SUCCEEDED,
                AuthProvider.GOOGLE, "SUCCESS");
    }

    @Test
    void linkGoogle_withStaleProof_rejectsBeforeTransaction() {
        UserEntity user = user(1L, "user@grun.app");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(googleVerifier.verify("google-token")).thenReturn(new VerifiedGoogleIdentityDto(
                "google-sub", "google@grun.app", "Google User", true, Instant.now().minusSeconds(600)));

        AccountLinkException exception = assertThrows(AccountLinkException.class,
                () -> service.linkGoogle(user.getEmail(), "google-token", "opaque-token"));

        assertEquals(AccountLinkErrorCode.REAUTHENTICATION_FAILED, exception.getCode());
        verify(transactionService, never()).link(any(), any(), any(), any(), any());
    }

    @Test
    void unlinkProvider_delegatesAuthorizationAndAuditsSuccess() {
        UserEntity user = user(1L, "user@grun.app");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        service.unlinkProvider(user.getEmail(), AuthProvider.APPLE, "opaque-token");

        verify(transactionService).unlink(user.getEmail(), AuthProvider.APPLE, "opaque-token");
        verify(auditService).record(user.getId(), AccountSecurityEventType.ACCOUNT_PROVIDER_UNLINKED,
                AuthProvider.APPLE, "SUCCESS");
    }

    @Test
    void updatePassword_whenPasswordWasNotUserManaged_doesNotRequireCurrentPassword() {
        UserEntity user = user(1L, "user@grun.app");
        user.setPasswordSet(false);
        AccountPasswordRequestDto request = new AccountPasswordRequestDto();
        request.setNewPassword("NewStrongPass1!");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(request.getNewPassword())).thenReturn("encoded");

        service.updatePassword(user.getEmail(), request);

        assertEquals("encoded", user.getPassword());
        assertTrue(user.getPasswordSet());
        verify(refreshTokenService).revokeAllForUser(user);
    }

    @Test
    void updatePassword_whenPasswordAlreadySet_requiresValidCurrentPassword() {
        UserEntity user = user(1L, "user@grun.app");
        user.setPassword("old-encoded");
        user.setPasswordSet(true);
        AccountPasswordRequestDto request = new AccountPasswordRequestDto();
        request.setCurrentPassword("wrong");
        request.setNewPassword("NewStrongPass1!");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "old-encoded")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> service.updatePassword(user.getEmail(), request));
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    private UserEntity user(Long id, String email) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    private FederatedIdentityEntity identity(UserEntity user, AuthProvider provider) {
        FederatedIdentityEntity identity = new FederatedIdentityEntity();
        identity.setUser(user);
        identity.setProvider(provider);
        identity.setProviderSubject(provider + "-sub");
        identity.setProviderEmail(provider.name().toLowerCase() + "@grun.app");
        identity.setCreatedAt(LocalDateTime.now());
        return identity;
    }
}