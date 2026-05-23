package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.VerifiedGoogleIdentityDto;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.service.GoogleIdTokenVerifierService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class NimbusGoogleIdTokenVerifierService implements GoogleIdTokenVerifierService {

    private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final List<String> GOOGLE_ISSUERS = List.of("accounts.google.com", "https://accounts.google.com");

    private final List<String> clientIds;
    private final JwtDecoder jwtDecoder;

    public NimbusGoogleIdTokenVerifierService(@Value("${grun.oauth.google.client-ids:}") String configuredClientIds) {
        this.clientIds = Arrays.stream(configuredClientIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI).build();
        OAuth2TokenValidator<Jwt> timestamp = new JwtTimestampValidator();
        OAuth2TokenValidator<Jwt> issuer = jwt -> GOOGLE_ISSUERS.contains(jwt.getIssuer() == null ? null : jwt.getIssuer().toString())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Google issuer is invalid.", null));
        OAuth2TokenValidator<Jwt> audience = jwt -> clientIds.stream().anyMatch(jwt.getAudience()::contains)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Google audience is invalid.", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp, issuer, audience));
        this.jwtDecoder = decoder;
    }

    @Override
    public VerifiedGoogleIdentityDto verify(String idToken) {
        if (clientIds.isEmpty()) {
            throw new IllegalArgumentException("Google login is not configured.");
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(idToken);
        } catch (JwtException ex) {
            throw new InvalidCredentialsException("Google ID token is invalid");
        }

        String subject = trimToNull(jwt.getSubject());
        String email = trimToNull(jwt.getClaimAsString("email"));
        boolean emailVerified = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));
        if (subject == null || email == null || !emailVerified) {
            throw new InvalidCredentialsException("Google account identity is incomplete or unverified");
        }

        return new VerifiedGoogleIdentityDto(subject, email, trimToNull(jwt.getClaimAsString("name")), true);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
