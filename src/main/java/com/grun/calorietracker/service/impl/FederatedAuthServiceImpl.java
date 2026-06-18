package com.grun.calorietracker.service.impl;

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
import com.grun.calorietracker.service.FederatedAuthService;
import com.grun.calorietracker.service.AppleIdTokenVerifierService;
import com.grun.calorietracker.service.GoogleIdTokenVerifierService;
import com.grun.calorietracker.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FederatedAuthServiceImpl implements FederatedAuthService {

    private final GoogleIdTokenVerifierService googleIdTokenVerifierService;
    private final AppleIdTokenVerifierService appleIdTokenVerifierService;
    private final FederatedIdentityRepository federatedIdentityRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional
    public AuthResponse loginWithGoogle(String idToken) {
        VerifiedGoogleIdentityDto identity = googleIdTokenVerifierService.verify(idToken);
        UserEntity user = federatedIdentityRepository
                .findByProviderAndProviderSubject(AuthProvider.GOOGLE, identity.subject())
                .map(FederatedIdentityEntity::getUser)
                .orElseGet(() -> linkIdentity(AuthProvider.GOOGLE, identity.subject(), identity.email(), identity.name(), identity.emailVerified()));

        return buildSession(user, "Google login successful");
    }

    @Override
    @Transactional
    public AuthResponse loginWithApple(String idToken, String nonce) {
        VerifiedAppleIdentityDto identity = appleIdTokenVerifierService.verify(idToken, nonce);
        UserEntity user = federatedIdentityRepository
                .findByProviderAndProviderSubject(AuthProvider.APPLE, identity.subject())
                .map(FederatedIdentityEntity::getUser)
                .orElseGet(() -> linkIdentity(AuthProvider.APPLE, identity.subject(), identity.email(), null, identity.emailVerified()));

        return buildSession(user, "Apple login successful");
    }

    private AuthResponse buildSession(UserEntity user, String message) {
        if (!isSessionAllowed(user)) {
            throw new InvalidCredentialsException("Account is disabled or locked");
        }
        return new AuthResponse(
                jwtUtil.generateToken(user.getEmail()),
                refreshTokenService.createRefreshToken(user),
                "Bearer",
                jwtUtil.getExpirationSeconds(),
                message
        );
    }

    private UserEntity linkIdentity(AuthProvider provider,
                                    String subject,
                                    String email,
                                    String name,
                                    boolean emailVerified) {
        if (email == null || !emailVerified) {
            throw new InvalidCredentialsException(provider + " account email is missing or unverified");
        }

        UserEntity user = userRepository.findByEmail(email)
                .map(existing -> verifyExistingProviderEmail(existing, emailVerified))
                .orElseGet(() -> createProviderUser(email, name, emailVerified));

        FederatedIdentityEntity federatedIdentity = new FederatedIdentityEntity();
        federatedIdentity.setUser(user);
        federatedIdentity.setProvider(provider);
        federatedIdentity.setProviderSubject(subject);
        federatedIdentity.setProviderEmail(email);
        federatedIdentity.setCreatedAt(LocalDateTime.now());
        federatedIdentityRepository.save(federatedIdentity);
        return user;
    }

    private boolean isSessionAllowed(UserEntity user) {
        return user != null
                && !Boolean.FALSE.equals(user.getAccountEnabled())
                && !Boolean.TRUE.equals(user.getAccountLocked())
                && (user.getLoginLockedUntil() == null || !user.getLoginLockedUntil().isAfter(LocalDateTime.now()));
    }

    private UserEntity verifyExistingProviderEmail(UserEntity user, boolean emailVerified) {
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            user.setEmailVerified(emailVerified);
            return userRepository.save(user);
        }
        return user;
    }

    private UserEntity createProviderUser(String email, String name, boolean emailVerified) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setName(name);
        user.setEmailVerified(emailVerified);
        user.setRole(UserRole.STANDARD);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setPasswordSet(false);
        return userRepository.save(user);
    }
}
