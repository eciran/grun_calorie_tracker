package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AccountLinkAuthorizationRequestDto;
import com.grun.calorietracker.dto.AccountLinkAuthorizationResponseDto;
import com.grun.calorietracker.dto.VerifiedAppleIdentityDto;
import com.grun.calorietracker.dto.VerifiedGoogleIdentityDto;
import com.grun.calorietracker.entity.AccountLinkAuthorizationEntity;
import com.grun.calorietracker.entity.FederatedIdentityEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AccountLinkErrorCode;
import com.grun.calorietracker.enums.AccountLinkPurpose;
import com.grun.calorietracker.enums.AccountReauthenticationMethod;
import com.grun.calorietracker.enums.AccountSecurityEventType;
import com.grun.calorietracker.enums.AuthProvider;
import com.grun.calorietracker.exception.AccountLinkException;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.AccountLinkAuthorizationRepository;
import com.grun.calorietracker.repository.FederatedIdentityRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AccountLinkAuthorizationService;
import com.grun.calorietracker.service.AccountSecurityAuditService;
import com.grun.calorietracker.service.AppleIdTokenVerifierService;
import com.grun.calorietracker.service.GoogleIdTokenVerifierService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AccountLinkAuthorizationServiceImpl implements AccountLinkAuthorizationService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final FederatedIdentityRepository federatedIdentityRepository;
    private final AccountLinkAuthorizationRepository authorizationRepository;
    private final GoogleIdTokenVerifierService googleVerifier;
    private final AppleIdTokenVerifierService appleVerifier;
    private final PasswordEncoder passwordEncoder;
    private final AccountSecurityAuditService auditService;

    @Value("${grun.account-link.authorization-ttl-seconds:300}")
    private long authorizationTtlSeconds;

    @Value("${grun.account-link.provider-proof-max-age-seconds:300}")
    private long providerProofMaxAgeSeconds;

    @Override
    public AccountLinkAuthorizationResponseDto createAuthorization(
            String userEmail,
            AccountLinkAuthorizationRequestDto request
    ) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> failure(AccountLinkErrorCode.REAUTHENTICATION_FAILED, "Reauthentication failed."));
        validateRequest(request);

        try {
            switch (request.getMethod()) {
                case PASSWORD -> verifyPassword(user, request.getCurrentPassword());
                case GOOGLE -> verifyGoogleProof(user, request.getIdToken());
                case APPLE -> verifyAppleProof(user, request.getIdToken(), request.getNonce());
            }
        } catch (AccountLinkException exception) {
            auditService.record(user.getId(), AccountSecurityEventType.ACCOUNT_LINK_REJECTED,
                    request.getTargetProvider(), exception.getCode().name());
            throw exception;
        }

        String rawToken = generateRawToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(authorizationTtlSeconds);
        AccountLinkAuthorizationEntity entity = new AccountLinkAuthorizationEntity();
        entity.setUser(user);
        entity.setPurpose(request.getPurpose());
        entity.setTargetProvider(request.getTargetProvider());
        entity.setReauthenticationMethod(request.getMethod());
        entity.setTokenHash(hash(rawToken));
        entity.setExpiresAt(expiresAt);
        authorizationRepository.save(entity);
        auditService.record(user.getId(), AccountSecurityEventType.ACCOUNT_LINK_AUTHORIZATION_CREATED,
                request.getTargetProvider(), "SUCCESS");
        return new AccountLinkAuthorizationResponseDto(rawToken, request.getPurpose(), request.getTargetProvider(), expiresAt);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consumeAuthorization(
            UserEntity user,
            String rawToken,
            AccountLinkPurpose purpose,
            AuthProvider targetProvider
    ) {
        if (rawToken == null || rawToken.isBlank()) {
            throw failure(AccountLinkErrorCode.LINK_AUTHORIZATION_INVALID, "Account authorization is required.");
        }
        AccountLinkAuthorizationEntity authorization = authorizationRepository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> failure(AccountLinkErrorCode.LINK_AUTHORIZATION_INVALID, "Account authorization is invalid."));
        if (!authorization.getUser().getId().equals(user.getId())
                || authorization.getPurpose() != purpose
                || authorization.getTargetProvider() != targetProvider) {
            throw failure(AccountLinkErrorCode.LINK_AUTHORIZATION_INVALID, "Account authorization does not match this operation.");
        }
        if (authorization.getUsedAt() != null) {
            throw failure(AccountLinkErrorCode.LINK_AUTHORIZATION_ALREADY_USED, "Account authorization was already used.");
        }
        if (authorization.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw failure(AccountLinkErrorCode.LINK_AUTHORIZATION_EXPIRED, "Account authorization has expired.");
        }
        authorization.setUsedAt(LocalDateTime.now());
        authorizationRepository.saveAndFlush(authorization);
    }

    private void verifyPassword(UserEntity user, String currentPassword) {
        if (!Boolean.TRUE.equals(user.getPasswordSet())
                || currentPassword == null
                || currentPassword.isBlank()
                || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw failure(AccountLinkErrorCode.REAUTHENTICATION_FAILED, "Password reauthentication failed.");
        }
    }

    private void verifyGoogleProof(UserEntity user, String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw failure(AccountLinkErrorCode.INVALID_LINK_REQUEST, "Google ID token is required.");
        }
        VerifiedGoogleIdentityDto verified;
        try {
            verified = googleVerifier.verify(idToken);
        } catch (InvalidCredentialsException exception) {
            throw failure(AccountLinkErrorCode.REAUTHENTICATION_FAILED, "Google reauthentication failed.");
        } catch (IllegalArgumentException exception) {
            throw failure(AccountLinkErrorCode.PROVIDER_NOT_CONFIGURED, "Google provider is not configured.");
        }
        requireFresh(verified.issuedAt());
        requireExistingIdentity(user, AuthProvider.GOOGLE, verified.subject());
    }

    private void verifyAppleProof(UserEntity user, String idToken, String nonce) {
        if (idToken == null || idToken.isBlank() || nonce == null || nonce.isBlank()) {
            throw failure(AccountLinkErrorCode.INVALID_LINK_REQUEST, "Apple ID token and nonce are required.");
        }
        VerifiedAppleIdentityDto verified;
        try {
            verified = appleVerifier.verify(idToken, nonce);
        } catch (InvalidCredentialsException exception) {
            throw failure(AccountLinkErrorCode.REAUTHENTICATION_FAILED, "Apple reauthentication failed.");
        } catch (IllegalArgumentException exception) {
            throw failure(AccountLinkErrorCode.PROVIDER_NOT_CONFIGURED, "Apple provider is not configured.");
        }
        requireFresh(verified.issuedAt());
        requireExistingIdentity(user, AuthProvider.APPLE, verified.subject());
    }

    private void requireExistingIdentity(UserEntity user, AuthProvider provider, String subject) {
        FederatedIdentityEntity identity = federatedIdentityRepository
                .findByProviderAndProviderSubject(provider, subject)
                .orElseThrow(() -> failure(AccountLinkErrorCode.REAUTHENTICATION_FAILED, "Provider proof is not linked to this account."));
        if (!identity.getUser().getId().equals(user.getId())) {
            throw failure(AccountLinkErrorCode.REAUTHENTICATION_FAILED, "Provider proof is not linked to this account.");
        }
    }

    private void requireFresh(Instant issuedAt) {
        Instant oldestAllowed = Instant.now().minus(providerProofMaxAgeSeconds, ChronoUnit.SECONDS);
        if (issuedAt == null || issuedAt.isBefore(oldestAllowed) || issuedAt.isAfter(Instant.now().plusSeconds(30))) {
            throw failure(AccountLinkErrorCode.REAUTHENTICATION_FAILED, "Provider proof is not recent.");
        }
    }

    private void validateRequest(AccountLinkAuthorizationRequestDto request) {
        if (request == null || request.getPurpose() == null || request.getTargetProvider() == null || request.getMethod() == null) {
            throw failure(AccountLinkErrorCode.INVALID_LINK_REQUEST, "Purpose, target provider and reauthentication method are required.");
        }
        if (request.getTargetProvider() != AuthProvider.GOOGLE && request.getTargetProvider() != AuthProvider.APPLE) {
            throw failure(AccountLinkErrorCode.INVALID_LINK_REQUEST, "Unsupported target provider.");
        }
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            return Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private AccountLinkException failure(AccountLinkErrorCode code, String message) {
        return new AccountLinkException(code, message);
    }
}
