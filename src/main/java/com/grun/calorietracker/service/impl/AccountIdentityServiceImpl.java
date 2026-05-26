package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AccountPasswordRequestDto;
import com.grun.calorietracker.dto.AccountPasswordResponseDto;
import com.grun.calorietracker.dto.LinkedIdentityDto;
import com.grun.calorietracker.dto.VerifiedAppleIdentityDto;
import com.grun.calorietracker.dto.VerifiedGoogleIdentityDto;
import com.grun.calorietracker.entity.FederatedIdentityEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AuthProvider;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.FederatedIdentityRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AccountIdentityService;
import com.grun.calorietracker.service.AppleIdTokenVerifierService;
import com.grun.calorietracker.service.GoogleIdTokenVerifierService;
import com.grun.calorietracker.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountIdentityServiceImpl implements AccountIdentityService {

    private final UserRepository userRepository;
    private final FederatedIdentityRepository federatedIdentityRepository;
    private final GoogleIdTokenVerifierService googleIdTokenVerifierService;
    private final AppleIdTokenVerifierService appleIdTokenVerifierService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional(readOnly = true)
    public List<LinkedIdentityDto> listLinkedIdentities(String userEmail) {
        return federatedIdentityRepository.findByUserEmailOrderByCreatedAtAsc(userEmail)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public LinkedIdentityDto linkGoogle(String userEmail, String idToken) {
        VerifiedGoogleIdentityDto identity = googleIdTokenVerifierService.verify(idToken);
        return linkIdentity(userEmail, AuthProvider.GOOGLE, identity.subject(), identity.email());
    }

    @Override
    @Transactional
    public LinkedIdentityDto linkApple(String userEmail, String idToken, String nonce) {
        VerifiedAppleIdentityDto identity = appleIdTokenVerifierService.verify(idToken, nonce);
        return linkIdentity(userEmail, AuthProvider.APPLE, identity.subject(), identity.email());
    }

    @Override
    @Transactional
    public void unlinkProvider(String userEmail, AuthProvider provider) {
        UserEntity user = findUser(userEmail);
        FederatedIdentityEntity identity = federatedIdentityRepository
                .findByUserEmailAndProvider(userEmail, provider)
                .orElseThrow(() -> new IllegalArgumentException("Provider identity is not linked to this account"));

        long linkedProviderCount = federatedIdentityRepository.countByUserEmail(userEmail);
        boolean hasPasswordLogin = Boolean.TRUE.equals(user.getPasswordSet());
        if (!hasPasswordLogin && linkedProviderCount <= 1) {
            throw new IllegalArgumentException("Cannot unlink the last available sign-in method");
        }

        federatedIdentityRepository.delete(identity);
    }

    @Override
    @Transactional
    public AccountPasswordResponseDto updatePassword(String userEmail, AccountPasswordRequestDto request) {
        UserEntity user = findUser(userEmail);
        if (Boolean.TRUE.equals(user.getPasswordSet())) {
            String currentPassword = request.getCurrentPassword();
            if (currentPassword == null || currentPassword.isBlank()
                    || !passwordEncoder.matches(currentPassword, user.getPassword())) {
                throw new InvalidCredentialsException("Current password is required or invalid");
            }
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordSet(true);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user);
        return new AccountPasswordResponseDto("Password updated successfully.");
    }

    private LinkedIdentityDto linkIdentity(String userEmail, AuthProvider provider, String subject, String providerEmail) {
        UserEntity currentUser = findUser(userEmail);
        FederatedIdentityEntity identity = federatedIdentityRepository
                .findByProviderAndProviderSubject(provider, subject)
                .orElse(null);

        if (identity != null) {
            if (identity.getUser().getId().equals(currentUser.getId())) {
                return toDto(identity);
            }
            throw new IllegalArgumentException("Provider identity is already linked to another account");
        }

        FederatedIdentityEntity newIdentity = new FederatedIdentityEntity();
        newIdentity.setUser(currentUser);
        newIdentity.setProvider(provider);
        newIdentity.setProviderSubject(subject);
        newIdentity.setProviderEmail(providerEmail);
        newIdentity.setCreatedAt(LocalDateTime.now());
        return toDto(federatedIdentityRepository.save(newIdentity));
    }

    private UserEntity findUser(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    private LinkedIdentityDto toDto(FederatedIdentityEntity identity) {
        return new LinkedIdentityDto(
                identity.getProvider(),
                identity.getProviderEmail(),
                identity.getCreatedAt()
        );
    }
}
