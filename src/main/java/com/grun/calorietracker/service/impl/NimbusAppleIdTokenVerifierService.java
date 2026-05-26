package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.VerifiedAppleIdentityDto;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.service.AppleIdTokenVerifierService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class NimbusAppleIdTokenVerifierService implements AppleIdTokenVerifierService {

    private static final String APPLE_JWKS_URI = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    private final List<String> clientIds;
    private final JwtDecoder jwtDecoder;

    public NimbusAppleIdTokenVerifierService(@Value("${grun.oauth.apple.client-ids:}") String configuredClientIds) {
        this.clientIds = Arrays.stream(configuredClientIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(APPLE_JWKS_URI).build();
        OAuth2TokenValidator<Jwt> timestamp = new JwtTimestampValidator();
        OAuth2TokenValidator<Jwt> issuer = jwt -> APPLE_ISSUER.equals(jwt.getIssuer() == null ? null : jwt.getIssuer().toString())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Apple issuer is invalid.", null));
        OAuth2TokenValidator<Jwt> audience = jwt -> clientIds.stream().anyMatch(jwt.getAudience()::contains)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Apple audience is invalid.", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp, issuer, audience));
        this.jwtDecoder = decoder;
    }

    @Override
    public VerifiedAppleIdentityDto verify(String idToken, String nonce) {
        if (clientIds.isEmpty()) {
            throw new IllegalArgumentException("Apple login is not configured.");
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(idToken);
        } catch (JwtException ex) {
            throw new InvalidCredentialsException("Apple ID token is invalid");
        }

        String subject = trimToNull(jwt.getSubject());
        String tokenNonce = trimToNull(jwt.getClaimAsString("nonce"));
        if (subject == null || tokenNonce == null || !tokenNonce.equals(nonce)) {
            throw new InvalidCredentialsException("Apple account identity or nonce is invalid");
        }

        return new VerifiedAppleIdentityDto(
                subject,
                trimToNull(jwt.getClaimAsString("email")),
                emailVerified(jwt.getClaim("email_verified"))
        );
    }

    private boolean emailVerified(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value instanceof String stringValue && Boolean.parseBoolean(stringValue);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
