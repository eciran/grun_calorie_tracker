package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AccountPasswordRequestDto;
import com.grun.calorietracker.dto.AccountPasswordResponseDto;
import com.grun.calorietracker.dto.LinkedIdentityDto;
import com.grun.calorietracker.dto.VerifiedAppleIdentityDto;
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
import com.grun.calorietracker.service.AccountIdentityService;
import com.grun.calorietracker.service.AccountSecurityAuditService;
import com.grun.calorietracker.service.AppleIdTokenVerifierService;
import com.grun.calorietracker.service.GoogleIdTokenVerifierService;
import com.grun.calorietracker.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountIdentityServiceImpl implements AccountIdentityService {

    private final UserRepository userRepository;
    private final FederatedIdentityRepository federatedIdentityRepository;
    private final GoogleIdTokenVerifierService googleVerifier;
    private final AppleIdTokenVerifierService appleVerifier;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AccountIdentityTransactionService transactionService;
    private final AccountSecurityAuditService auditService;

    @Value("${grun.account-link.provider-proof-max-age-seconds:300}")
    private long providerProofMaxAgeSeconds;

    @Override
    @Transactional(readOnly = true)
    public List<LinkedIdentityDto> listLinkedIdentities(String userEmail) {
        return federatedIdentityRepository.findByUserEmailOrderByCreatedAtAsc(userEmail)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public LinkedIdentityDto linkGoogle(String userEmail, String idToken, String authorizationToken) {
        UserEntity user = findUser(userEmail);
        try {
            VerifiedGoogleIdentityDto identity = googleVerifier.verify(idToken);
            requireFresh(identity.issuedAt());
            LinkedIdentityDto linkedIdentity = transactionService.link(userEmail, AuthProvider.GOOGLE,
                    identity.subject(), identity.email(), authorizationToken);
            auditService.record(user.getId(), AccountSecurityEventType.ACCOUNT_LINK_SUCCEEDED,
                    AuthProvider.GOOGLE, "SUCCESS");
            return linkedIdentity;
        } catch (AccountLinkException exception) {
            recordRejected(user, AuthProvider.GOOGLE, exception);
            throw exception;
        } catch (InvalidCredentialsException exception) {
            AccountLinkException mapped = failure(AccountLinkErrorCode.REAUTHENTICATION_FAILED,
                    "Google identity token is invalid.");
            recordRejected(user, AuthProvider.GOOGLE, mapped);
            throw mapped;
        } catch (IllegalArgumentException exception) {
            AccountLinkException mapped = failure(AccountLinkErrorCode.PROVIDER_NOT_CONFIGURED,
                    "Google provider is not configured.");
            recordRejected(user, AuthProvider.GOOGLE, mapped);
            throw mapped;
        }
    }

    @Override
    public LinkedIdentityDto linkApple(
            String userEmail,
            String idToken,
            String nonce,
            String authorizationToken
    ) {
        UserEntity user = findUser(userEmail);
        try {
            VerifiedAppleIdentityDto identity = appleVerifier.verify(idToken, nonce);
            requireFresh(identity.issuedAt());
            LinkedIdentityDto linkedIdentity = transactionService.link(userEmail, AuthProvider.APPLE,
                    identity.subject(), identity.email(), authorizationToken);
            auditService.record(user.getId(), AccountSecurityEventType.ACCOUNT_LINK_SUCCEEDED,
                    AuthProvider.APPLE, "SUCCESS");
            return linkedIdentity;
        } catch (AccountLinkException exception) {
            recordRejected(user, AuthProvider.APPLE, exception);
            throw exception;
        } catch (InvalidCredentialsException exception) {
            AccountLinkException mapped = failure(AccountLinkErrorCode.REAUTHENTICATION_FAILED,
                    "Apple identity token or nonce is invalid.");
            recordRejected(user, AuthProvider.APPLE, mapped);
            throw mapped;
        } catch (IllegalArgumentException exception) {
            AccountLinkException mapped = failure(AccountLinkErrorCode.PROVIDER_NOT_CONFIGURED,
                    "Apple provider is not configured.");
            recordRejected(user, AuthProvider.APPLE, mapped);
            throw mapped;
        }
    }

    @Override
    public void unlinkProvider(String userEmail, AuthProvider provider, String authorizationToken) {
        UserEntity user = findUser(userEmail);
        try {
            transactionService.unlink(userEmail, provider, authorizationToken);
            auditService.record(user.getId(), AccountSecurityEventType.ACCOUNT_PROVIDER_UNLINKED,
                    provider, "SUCCESS");
        } catch (AccountLinkException exception) {
            recordRejected(user, provider, exception);
            throw exception;
        }
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

    private void requireFresh(Instant issuedAt) {
        Instant now = Instant.now();
        Instant oldestAllowed = now.minus(providerProofMaxAgeSeconds, ChronoUnit.SECONDS);
        if (issuedAt == null || issuedAt.isBefore(oldestAllowed) || issuedAt.isAfter(now.plusSeconds(30))) {
            throw failure(AccountLinkErrorCode.REAUTHENTICATION_FAILED,
                    "Provider identity token is not recent.");
        }
    }
    private void recordRejected(UserEntity user, AuthProvider provider, AccountLinkException exception) {
        auditService.record(user.getId(), AccountSecurityEventType.ACCOUNT_LINK_REJECTED,
                provider, exception.getCode().name());
    }

    private UserEntity findUser(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    private LinkedIdentityDto toDto(FederatedIdentityEntity identity) {
        return new LinkedIdentityDto(identity.getProvider(), identity.getProviderEmail(), identity.getCreatedAt());
    }

    private AccountLinkException failure(AccountLinkErrorCode code, String message) {
        return new AccountLinkException(code, message);
    }
}
