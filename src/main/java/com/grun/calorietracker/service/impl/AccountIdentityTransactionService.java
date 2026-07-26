package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.LinkedIdentityDto;
import com.grun.calorietracker.entity.FederatedIdentityEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AccountLinkErrorCode;
import com.grun.calorietracker.enums.AccountLinkPurpose;
import com.grun.calorietracker.enums.AuthProvider;
import com.grun.calorietracker.exception.AccountLinkException;
import com.grun.calorietracker.repository.FederatedIdentityRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AccountLinkAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AccountIdentityTransactionService {
    private final UserRepository userRepository;
    private final FederatedIdentityRepository identityRepository;
    private final AccountLinkAuthorizationService authorizationService;

    @Transactional
    public LinkedIdentityDto link(
            String userEmail,
            AuthProvider provider,
            String subject,
            String providerEmail,
            String authorizationToken
    ) {
        UserEntity user = lockUser(userEmail);
        authorizationService.consumeAuthorization(user, authorizationToken, AccountLinkPurpose.ACCOUNT_LINK, provider);

        FederatedIdentityEntity subjectIdentity = identityRepository
                .findByProviderAndProviderSubject(provider, subject)
                .orElse(null);
        if (subjectIdentity != null) {
            if (subjectIdentity.getUser().getId().equals(user.getId())) {
                return toDto(subjectIdentity);
            }
            throw failure(AccountLinkErrorCode.PROVIDER_IDENTITY_IN_USE,
                    "Provider identity is already linked to another account.");
        }

        FederatedIdentityEntity providerIdentity = identityRepository
                .findByUserIdAndProvider(user.getId(), provider)
                .orElse(null);
        if (providerIdentity != null) {
            throw failure(AccountLinkErrorCode.PROVIDER_ALREADY_LINKED,
                    "A different identity for this provider is already linked.");
        }

        FederatedIdentityEntity newIdentity = new FederatedIdentityEntity();
        newIdentity.setUser(user);
        newIdentity.setProvider(provider);
        newIdentity.setProviderSubject(subject);
        newIdentity.setProviderEmail(providerEmail);
        newIdentity.setCreatedAt(LocalDateTime.now());
        try {
            return toDto(identityRepository.saveAndFlush(newIdentity));
        } catch (DataIntegrityViolationException exception) {
            throw resolveUniquenessRace(exception);
        }
    }

    @Transactional
    public void unlink(String userEmail, AuthProvider provider, String authorizationToken) {
        UserEntity user = lockUser(userEmail);
        authorizationService.consumeAuthorization(user, authorizationToken, AccountLinkPurpose.ACCOUNT_UNLINK, provider);
        FederatedIdentityEntity identity = identityRepository.findByUserIdAndProvider(user.getId(), provider)
                .orElseThrow(() -> failure(AccountLinkErrorCode.PROVIDER_NOT_LINKED,
                        "Provider identity is not linked to this account."));

        long providerCount = identityRepository.countByUser(user);
        if (!Boolean.TRUE.equals(user.getPasswordSet()) && providerCount <= 1) {
            throw failure(AccountLinkErrorCode.LAST_SIGN_IN_METHOD,
                    "Cannot unlink the last available sign-in method.");
        }

        identityRepository.delete(identity);
        identityRepository.flush();
    }

    private AccountLinkException resolveUniquenessRace(DataIntegrityViolationException exception) {
        if (exceptionDetails(exception).contains("uq_federated_identity_provider_subject")) {
            return failure(AccountLinkErrorCode.PROVIDER_IDENTITY_IN_USE,
                    "Provider identity is already linked to another account.");
        }
        return failure(AccountLinkErrorCode.PROVIDER_ALREADY_LINKED,
                "An identity for this provider is already linked.");
    }

    private String exceptionDetails(Throwable exception) {
        StringBuilder details = new StringBuilder();
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null) {
                details.append(' ').append(current.getMessage().toLowerCase());
            }
            current = current.getCause();
        }
        return details.toString();
    }

    private UserEntity lockUser(String email) {
        return userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    private LinkedIdentityDto toDto(FederatedIdentityEntity identity) {
        return new LinkedIdentityDto(identity.getProvider(), identity.getProviderEmail(), identity.getCreatedAt());
    }

    private AccountLinkException failure(AccountLinkErrorCode code, String message) {
        return new AccountLinkException(code, message);
    }
}