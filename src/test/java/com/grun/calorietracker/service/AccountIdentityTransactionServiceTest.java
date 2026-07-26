package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FederatedIdentityEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AccountLinkErrorCode;
import com.grun.calorietracker.enums.AccountLinkPurpose;
import com.grun.calorietracker.enums.AuthProvider;
import com.grun.calorietracker.exception.AccountLinkException;
import com.grun.calorietracker.repository.FederatedIdentityRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.AccountIdentityTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountIdentityTransactionServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private FederatedIdentityRepository identityRepository;
    @Mock private AccountLinkAuthorizationService authorizationService;

    private AccountIdentityTransactionService service;

    @BeforeEach
    void setUp() {
        service = new AccountIdentityTransactionService(userRepository, identityRepository, authorizationService);
    }

    @Test
    void link_rejectsProviderSubjectOwnedByAnotherUser() {
        UserEntity current = user(1L, "current@grun.app", true);
        UserEntity other = user(2L, "other@grun.app", true);
        when(userRepository.findByEmailForUpdate(current.getEmail())).thenReturn(Optional.of(current));
        when(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(identity(other, AuthProvider.GOOGLE, "google-sub")));

        AccountLinkException exception = assertThrows(AccountLinkException.class,
                () -> service.link(current.getEmail(), AuthProvider.GOOGLE,
                        "google-sub", "google@grun.app", "opaque-token"));

        assertEquals(AccountLinkErrorCode.PROVIDER_IDENTITY_IN_USE, exception.getCode());
        verify(authorizationService).consumeAuthorization(
                current, "opaque-token", AccountLinkPurpose.ACCOUNT_LINK, AuthProvider.GOOGLE);
        verify(identityRepository, never()).saveAndFlush(any());
    }

    @Test
    void link_rejectsSecondIdentityForSameUserAndProvider() {
        UserEntity current = user(1L, "current@grun.app", true);
        when(userRepository.findByEmailForUpdate(current.getEmail())).thenReturn(Optional.of(current));
        when(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "new-google-sub"))
                .thenReturn(Optional.empty());
        when(identityRepository.findByUserIdAndProvider(current.getId(), AuthProvider.GOOGLE))
                .thenReturn(Optional.of(identity(current, AuthProvider.GOOGLE, "old-google-sub")));

        AccountLinkException exception = assertThrows(AccountLinkException.class,
                () -> service.link(current.getEmail(), AuthProvider.GOOGLE,
                        "new-google-sub", "new@grun.app", "opaque-token"));

        assertEquals(AccountLinkErrorCode.PROVIDER_ALREADY_LINKED, exception.getCode());
        verify(identityRepository, never()).saveAndFlush(any());
    }

    @Test
    void unlink_rejectsLastSignInMethodWhenNoPasswordExists() {
        UserEntity current = user(1L, "current@grun.app", false);
        FederatedIdentityEntity apple = identity(current, AuthProvider.APPLE, "apple-sub");
        when(userRepository.findByEmailForUpdate(current.getEmail())).thenReturn(Optional.of(current));
        when(identityRepository.findByUserIdAndProvider(current.getId(), AuthProvider.APPLE))
                .thenReturn(Optional.of(apple));
        when(identityRepository.countByUser(current)).thenReturn(1L);

        AccountLinkException exception = assertThrows(AccountLinkException.class,
                () -> service.unlink(current.getEmail(), AuthProvider.APPLE, "opaque-token"));

        assertEquals(AccountLinkErrorCode.LAST_SIGN_IN_METHOD, exception.getCode());
        verify(identityRepository, never()).delete(any());
    }

    private UserEntity user(Long id, String email, boolean passwordSet) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(email);
        user.setPasswordSet(passwordSet);
        return user;
    }

    private FederatedIdentityEntity identity(UserEntity user, AuthProvider provider, String subject) {
        FederatedIdentityEntity identity = new FederatedIdentityEntity();
        identity.setUser(user);
        identity.setProvider(provider);
        identity.setProviderSubject(subject);
        identity.setProviderEmail(provider.name().toLowerCase() + "@grun.app");
        identity.setCreatedAt(LocalDateTime.now());
        return identity;
    }
}