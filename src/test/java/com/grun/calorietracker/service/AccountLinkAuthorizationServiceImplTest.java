package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AccountLinkAuthorizationRequestDto;
import com.grun.calorietracker.dto.AccountLinkAuthorizationResponseDto;
import com.grun.calorietracker.entity.AccountLinkAuthorizationEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AccountLinkErrorCode;
import com.grun.calorietracker.enums.AccountLinkPurpose;
import com.grun.calorietracker.enums.AccountReauthenticationMethod;
import com.grun.calorietracker.enums.AuthProvider;
import com.grun.calorietracker.exception.AccountLinkException;
import com.grun.calorietracker.repository.AccountLinkAuthorizationRepository;
import com.grun.calorietracker.repository.FederatedIdentityRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.AccountLinkAuthorizationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountLinkAuthorizationServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private FederatedIdentityRepository identityRepository;
    @Mock private AccountLinkAuthorizationRepository authorizationRepository;
    @Mock private GoogleIdTokenVerifierService googleVerifier;
    @Mock private AppleIdTokenVerifierService appleVerifier;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AccountSecurityAuditService auditService;

    private AccountLinkAuthorizationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AccountLinkAuthorizationServiceImpl(
                userRepository,
                identityRepository,
                authorizationRepository,
                googleVerifier,
                appleVerifier,
                passwordEncoder,
                auditService
        );
        ReflectionTestUtils.setField(service, "authorizationTtlSeconds", 300L);
        ReflectionTestUtils.setField(service, "providerProofMaxAgeSeconds", 300L);
    }

    @Test
    void createAuthorization_withPassword_storesOnlyHashAndReturnsRawTokenOnce() {
        UserEntity user = user(1L, "user@grun.app");
        user.setPasswordSet(true);
        user.setPassword("encoded");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("CurrentPass1!", "encoded")).thenReturn(true);
        AccountLinkAuthorizationRequestDto request = request(
                AccountLinkPurpose.ACCOUNT_LINK, AuthProvider.GOOGLE, AccountReauthenticationMethod.PASSWORD);
        request.setCurrentPassword("CurrentPass1!");

        AccountLinkAuthorizationResponseDto response = service.createAuthorization(user.getEmail(), request);

        ArgumentCaptor<AccountLinkAuthorizationEntity> captor =
                ArgumentCaptor.forClass(AccountLinkAuthorizationEntity.class);
        verify(authorizationRepository).save(captor.capture());
        assertNotNull(response.authorizationToken());
        assertNotEquals(response.authorizationToken(), captor.getValue().getTokenHash());
        assertEquals(AccountLinkPurpose.ACCOUNT_LINK, captor.getValue().getPurpose());
        assertEquals(AuthProvider.GOOGLE, captor.getValue().getTargetProvider());
    }

    @Test
    void createAuthorization_withWrongPassword_isRejected() {
        UserEntity user = user(1L, "user@grun.app");
        user.setPasswordSet(true);
        user.setPassword("encoded");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);
        AccountLinkAuthorizationRequestDto request = request(
                AccountLinkPurpose.ACCOUNT_LINK, AuthProvider.GOOGLE, AccountReauthenticationMethod.PASSWORD);
        request.setCurrentPassword("wrong");

        AccountLinkException exception = assertThrows(AccountLinkException.class,
                () -> service.createAuthorization(user.getEmail(), request));

        assertEquals(AccountLinkErrorCode.REAUTHENTICATION_FAILED, exception.getCode());
        verify(authorizationRepository, never()).save(any());
    }

    @Test
    void consumeAuthorization_marksMatchingTokenUsed() {
        UserEntity user = user(1L, "user@grun.app");
        AccountLinkAuthorizationEntity authorization = authorization(user, AccountLinkPurpose.ACCOUNT_UNLINK,
                AuthProvider.APPLE, LocalDateTime.now().plusMinutes(5), null);
        when(authorizationRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(authorization));

        service.consumeAuthorization(user, "raw-token", AccountLinkPurpose.ACCOUNT_UNLINK, AuthProvider.APPLE);

        assertNotNull(authorization.getUsedAt());
        verify(authorizationRepository).saveAndFlush(authorization);
    }

    @Test
    void consumeAuthorization_rejectsUsedToken() {
        UserEntity user = user(1L, "user@grun.app");
        AccountLinkAuthorizationEntity authorization = authorization(user, AccountLinkPurpose.ACCOUNT_LINK,
                AuthProvider.GOOGLE, LocalDateTime.now().plusMinutes(5), LocalDateTime.now());
        when(authorizationRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(authorization));

        AccountLinkException exception = assertThrows(AccountLinkException.class,
                () -> service.consumeAuthorization(user, "raw-token", AccountLinkPurpose.ACCOUNT_LINK, AuthProvider.GOOGLE));

        assertEquals(AccountLinkErrorCode.LINK_AUTHORIZATION_ALREADY_USED, exception.getCode());
    }

    @Test
    void consumeAuthorization_rejectsExpiredToken() {
        UserEntity user = user(1L, "user@grun.app");
        AccountLinkAuthorizationEntity authorization = authorization(user, AccountLinkPurpose.ACCOUNT_LINK,
                AuthProvider.GOOGLE, LocalDateTime.now().minusSeconds(1), null);
        when(authorizationRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(authorization));

        AccountLinkException exception = assertThrows(AccountLinkException.class,
                () -> service.consumeAuthorization(user, "raw-token", AccountLinkPurpose.ACCOUNT_LINK, AuthProvider.GOOGLE));

        assertEquals(AccountLinkErrorCode.LINK_AUTHORIZATION_EXPIRED, exception.getCode());
    }

    @Test
    void consumeAuthorization_rejectsWrongPurposeOrProvider() {
        UserEntity user = user(1L, "user@grun.app");
        AccountLinkAuthorizationEntity authorization = authorization(user, AccountLinkPurpose.ACCOUNT_LINK,
                AuthProvider.GOOGLE, LocalDateTime.now().plusMinutes(5), null);
        when(authorizationRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(authorization));

        AccountLinkException exception = assertThrows(AccountLinkException.class,
                () -> service.consumeAuthorization(user, "raw-token", AccountLinkPurpose.ACCOUNT_UNLINK, AuthProvider.APPLE));

        assertEquals(AccountLinkErrorCode.LINK_AUTHORIZATION_INVALID, exception.getCode());
    }

    private AccountLinkAuthorizationRequestDto request(
            AccountLinkPurpose purpose,
            AuthProvider provider,
            AccountReauthenticationMethod method
    ) {
        AccountLinkAuthorizationRequestDto request = new AccountLinkAuthorizationRequestDto();
        request.setPurpose(purpose);
        request.setTargetProvider(provider);
        request.setMethod(method);
        return request;
    }

    private AccountLinkAuthorizationEntity authorization(
            UserEntity user,
            AccountLinkPurpose purpose,
            AuthProvider provider,
            LocalDateTime expiresAt,
            LocalDateTime usedAt
    ) {
        AccountLinkAuthorizationEntity authorization = new AccountLinkAuthorizationEntity();
        authorization.setUser(user);
        authorization.setPurpose(purpose);
        authorization.setTargetProvider(provider);
        authorization.setReauthenticationMethod(AccountReauthenticationMethod.PASSWORD);
        authorization.setTokenHash("hash");
        authorization.setExpiresAt(expiresAt);
        authorization.setUsedAt(usedAt);
        return authorization;
    }

    private UserEntity user(Long id, String email) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(email);
        return user;
    }
}