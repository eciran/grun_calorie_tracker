package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AccountPasswordRequestDto;
import com.grun.calorietracker.dto.LinkedIdentityDto;
import com.grun.calorietracker.dto.VerifiedGoogleIdentityDto;
import com.grun.calorietracker.entity.FederatedIdentityEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AuthProvider;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.FederatedIdentityRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.AccountIdentityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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

    @Mock
    private UserRepository userRepository;

    @Mock
    private FederatedIdentityRepository federatedIdentityRepository;

    @Mock
    private GoogleIdTokenVerifierService googleIdTokenVerifierService;

    @Mock
    private AppleIdTokenVerifierService appleIdTokenVerifierService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AccountIdentityServiceImpl accountIdentityService;

    @BeforeEach
    void setUp() {
        accountIdentityService = new AccountIdentityServiceImpl(
                userRepository,
                federatedIdentityRepository,
                googleIdTokenVerifierService,
                appleIdTokenVerifierService,
                passwordEncoder,
                refreshTokenService
        );
    }

    @Test
    void listLinkedIdentities_returnsProviderDtos() {
        FederatedIdentityEntity identity = identity(user(1L, "user@grun.app"), AuthProvider.GOOGLE, "google@grun.app");
        when(federatedIdentityRepository.findByUserEmailOrderByCreatedAtAsc("user@grun.app"))
                .thenReturn(List.of(identity));

        List<LinkedIdentityDto> result = accountIdentityService.listLinkedIdentities("user@grun.app");

        assertEquals(1, result.size());
        assertEquals(AuthProvider.GOOGLE, result.get(0).provider());
        assertEquals("google@grun.app", result.get(0).providerEmail());
    }

    @Test
    void linkGoogle_whenIdentityIsUnused_linksToCurrentUser() {
        UserEntity user = user(1L, "user@grun.app");
        when(googleIdTokenVerifierService.verify("google-token"))
                .thenReturn(new VerifiedGoogleIdentityDto("google-sub", "google@grun.app", "Google User", true));
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(federatedIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(federatedIdentityRepository.save(any(FederatedIdentityEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        accountIdentityService.linkGoogle("user@grun.app", "google-token");

        ArgumentCaptor<FederatedIdentityEntity> captor = ArgumentCaptor.forClass(FederatedIdentityEntity.class);
        verify(federatedIdentityRepository).save(captor.capture());
        assertEquals(user, captor.getValue().getUser());
        assertEquals(AuthProvider.GOOGLE, captor.getValue().getProvider());
        assertEquals("google-sub", captor.getValue().getProviderSubject());
    }

    @Test
    void linkGoogle_whenIdentityBelongsToAnotherUser_rejectsLink() {
        UserEntity currentUser = user(1L, "user@grun.app");
        UserEntity otherUser = user(2L, "other@grun.app");
        when(googleIdTokenVerifierService.verify("google-token"))
                .thenReturn(new VerifiedGoogleIdentityDto("google-sub", "google@grun.app", "Google User", true));
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(currentUser));
        when(federatedIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(identity(otherUser, AuthProvider.GOOGLE, "google@grun.app")));

        assertThrows(IllegalArgumentException.class,
                () -> accountIdentityService.linkGoogle("user@grun.app", "google-token"));
        verify(federatedIdentityRepository, never()).save(any(FederatedIdentityEntity.class));
    }

    @Test
    void updatePassword_whenPasswordWasNotUserManaged_doesNotRequireCurrentPassword() {
        UserEntity user = user(1L, "user@grun.app");
        user.setPasswordSet(false);
        AccountPasswordRequestDto request = new AccountPasswordRequestDto();
        request.setNewPassword("NewStrongPass1!");
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewStrongPass1!")).thenReturn("encoded");

        accountIdentityService.updatePassword("user@grun.app", request);

        assertEquals("encoded", user.getPassword());
        assertTrue(user.getPasswordSet());
        verify(refreshTokenService).revokeAllForUser(user);
    }

    @Test
    void unlinkProvider_whenPasswordExists_allowsRemovingOnlyProvider() {
        UserEntity user = user(1L, "user@grun.app");
        user.setPasswordSet(true);
        FederatedIdentityEntity identity = identity(user, AuthProvider.GOOGLE, "google@grun.app");
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(federatedIdentityRepository.findByUserEmailAndProvider("user@grun.app", AuthProvider.GOOGLE))
                .thenReturn(Optional.of(identity));
        when(federatedIdentityRepository.countByUserEmail("user@grun.app")).thenReturn(1L);

        accountIdentityService.unlinkProvider("user@grun.app", AuthProvider.GOOGLE);

        verify(federatedIdentityRepository).delete(identity);
    }

    @Test
    void unlinkProvider_whenNoPasswordAndOnlyProvider_rejectsRemoval() {
        UserEntity user = user(1L, "user@grun.app");
        user.setPasswordSet(false);
        FederatedIdentityEntity identity = identity(user, AuthProvider.APPLE, "relay@apple.test");
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(federatedIdentityRepository.findByUserEmailAndProvider("user@grun.app", AuthProvider.APPLE))
                .thenReturn(Optional.of(identity));
        when(federatedIdentityRepository.countByUserEmail("user@grun.app")).thenReturn(1L);

        assertThrows(IllegalArgumentException.class,
                () -> accountIdentityService.unlinkProvider("user@grun.app", AuthProvider.APPLE));
        verify(federatedIdentityRepository, never()).delete(any(FederatedIdentityEntity.class));
    }

    @Test
    void unlinkProvider_whenNoPasswordButMultipleProviders_allowsRemoval() {
        UserEntity user = user(1L, "user@grun.app");
        user.setPasswordSet(false);
        FederatedIdentityEntity identity = identity(user, AuthProvider.GOOGLE, "google@grun.app");
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(federatedIdentityRepository.findByUserEmailAndProvider("user@grun.app", AuthProvider.GOOGLE))
                .thenReturn(Optional.of(identity));
        when(federatedIdentityRepository.countByUserEmail("user@grun.app")).thenReturn(2L);

        accountIdentityService.unlinkProvider("user@grun.app", AuthProvider.GOOGLE);

        verify(federatedIdentityRepository).delete(identity);
    }

    @Test
    void updatePassword_whenPasswordAlreadySet_requiresValidCurrentPassword() {
        UserEntity user = user(1L, "user@grun.app");
        user.setPassword("old-encoded");
        user.setPasswordSet(true);
        AccountPasswordRequestDto request = new AccountPasswordRequestDto();
        request.setCurrentPassword("wrong");
        request.setNewPassword("NewStrongPass1!");
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "old-encoded")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> accountIdentityService.updatePassword("user@grun.app", request));
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    private UserEntity user(Long id, String email) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    private FederatedIdentityEntity identity(UserEntity user, AuthProvider provider, String providerEmail) {
        FederatedIdentityEntity identity = new FederatedIdentityEntity();
        identity.setUser(user);
        identity.setProvider(provider);
        identity.setProviderSubject(provider + "-sub");
        identity.setProviderEmail(providerEmail);
        identity.setCreatedAt(java.time.LocalDateTime.now());
        return identity;
    }
}
