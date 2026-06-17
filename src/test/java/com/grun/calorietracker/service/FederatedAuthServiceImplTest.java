package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AuthResponse;
import com.grun.calorietracker.dto.VerifiedAppleIdentityDto;
import com.grun.calorietracker.dto.VerifiedGoogleIdentityDto;
import com.grun.calorietracker.entity.FederatedIdentityEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AuthProvider;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.FederatedIdentityRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.service.impl.FederatedAuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FederatedAuthServiceImplTest {

    @Mock
    private GoogleIdTokenVerifierService googleIdTokenVerifierService;

    @Mock
    private AppleIdTokenVerifierService appleIdTokenVerifierService;

    @Mock
    private FederatedIdentityRepository federatedIdentityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RefreshTokenService refreshTokenService;

    private FederatedAuthServiceImpl federatedAuthService;

    @BeforeEach
    void setUp() {
        federatedAuthService = new FederatedAuthServiceImpl(
                googleIdTokenVerifierService,
                appleIdTokenVerifierService,
                federatedIdentityRepository,
                userRepository,
                passwordEncoder,
                jwtUtil,
                refreshTokenService
        );
        lenient().when(jwtUtil.getExpirationSeconds()).thenReturn(900L);
    }

    @Test
    void loginWithGoogle_existingProviderIdentity_returnsGrunSession() {
        UserEntity user = user("oauth@grun.app", true);
        FederatedIdentityEntity identity = new FederatedIdentityEntity();
        identity.setUser(user);
        when(googleIdTokenVerifierService.verify("raw-id-token"))
                .thenReturn(new VerifiedGoogleIdentityDto("google-sub", user.getEmail(), "OAuth User", true));
        when(federatedIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(identity));
        when(jwtUtil.generateToken(user.getEmail())).thenReturn("access");
        when(refreshTokenService.createRefreshToken(user)).thenReturn("refresh");

        AuthResponse response = federatedAuthService.loginWithGoogle("raw-id-token");

        assertEquals("access", response.getToken());
        assertEquals("refresh", response.getRefreshToken());
    }

    @Test
    void loginWithGoogle_newProviderIdentity_createsVerifiedStandardUserAndLink() {
        VerifiedGoogleIdentityDto googleIdentity = new VerifiedGoogleIdentityDto(
                "google-new-sub",
                "new@grun.app",
                "New Google User",
                true
        );
        when(googleIdTokenVerifierService.verify("raw-id-token")).thenReturn(googleIdentity);
        when(federatedIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-new-sub"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@grun.app")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded-random-password");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtil.generateToken("new@grun.app")).thenReturn("access");
        when(refreshTokenService.createRefreshToken(any(UserEntity.class))).thenReturn("refresh");

        federatedAuthService.loginWithGoogle("raw-id-token");

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(UserRole.STANDARD, userCaptor.getValue().getRole());
        assertTrue(userCaptor.getValue().getEmailVerified());
        assertEquals(false, userCaptor.getValue().getPasswordSet());
        assertEquals("encoded-random-password", userCaptor.getValue().getPassword());

        ArgumentCaptor<FederatedIdentityEntity> identityCaptor = ArgumentCaptor.forClass(FederatedIdentityEntity.class);
        verify(federatedIdentityRepository).save(identityCaptor.capture());
        assertEquals(AuthProvider.GOOGLE, identityCaptor.getValue().getProvider());
        assertEquals("google-new-sub", identityCaptor.getValue().getProviderSubject());
    }

    @Test
    void loginWithApple_existingProviderIdentity_doesNotNeedEmailClaimForSession() {
        UserEntity user = user("apple@grun.app", true);
        FederatedIdentityEntity identity = new FederatedIdentityEntity();
        identity.setUser(user);
        when(appleIdTokenVerifierService.verify("apple-id-token", "apple-nonce"))
                .thenReturn(new VerifiedAppleIdentityDto("apple-sub", null, false));
        when(federatedIdentityRepository.findByProviderAndProviderSubject(AuthProvider.APPLE, "apple-sub"))
                .thenReturn(Optional.of(identity));
        when(jwtUtil.generateToken(user.getEmail())).thenReturn("apple-access");
        when(refreshTokenService.createRefreshToken(user)).thenReturn("apple-refresh");

        AuthResponse response = federatedAuthService.loginWithApple("apple-id-token", "apple-nonce");

        assertEquals("apple-access", response.getToken());
        assertEquals("apple-refresh", response.getRefreshToken());
    }

    @Test
    void loginWithGoogle_whenExistingAccountDisabled_rejectsSession() {
        UserEntity user = user("oauth@grun.app", true);
        user.setAccountEnabled(false);
        FederatedIdentityEntity identity = new FederatedIdentityEntity();
        identity.setUser(user);
        when(googleIdTokenVerifierService.verify("raw-id-token"))
                .thenReturn(new VerifiedGoogleIdentityDto("google-sub", user.getEmail(), "OAuth User", true));
        when(federatedIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(identity));

        assertThrows(InvalidCredentialsException.class, () -> federatedAuthService.loginWithGoogle("raw-id-token"));

        verify(jwtUtil, never()).generateToken(any());
        verify(refreshTokenService, never()).createRefreshToken(any());
    }

    @Test
    void loginWithApple_newProviderIdentity_createsVerifiedStandardUserAndLink() {
        VerifiedAppleIdentityDto appleIdentity = new VerifiedAppleIdentityDto(
                "apple-new-sub",
                "relay@privaterelay.appleid.com",
                true
        );
        when(appleIdTokenVerifierService.verify("apple-id-token", "apple-nonce")).thenReturn(appleIdentity);
        when(federatedIdentityRepository.findByProviderAndProviderSubject(AuthProvider.APPLE, "apple-new-sub"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("relay@privaterelay.appleid.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded-apple-password");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtil.generateToken("relay@privaterelay.appleid.com")).thenReturn("access");
        when(refreshTokenService.createRefreshToken(any(UserEntity.class))).thenReturn("refresh");

        federatedAuthService.loginWithApple("apple-id-token", "apple-nonce");

        ArgumentCaptor<FederatedIdentityEntity> identityCaptor = ArgumentCaptor.forClass(FederatedIdentityEntity.class);
        verify(federatedIdentityRepository).save(identityCaptor.capture());
        assertEquals(AuthProvider.APPLE, identityCaptor.getValue().getProvider());
        assertEquals("apple-new-sub", identityCaptor.getValue().getProviderSubject());
    }

    private UserEntity user(String email, boolean emailVerified) {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setEmail(email);
        user.setEmailVerified(emailVerified);
        user.setRole(UserRole.STANDARD);
        user.setAccountEnabled(true);
        user.setAccountLocked(false);
        return user;
    }
}
