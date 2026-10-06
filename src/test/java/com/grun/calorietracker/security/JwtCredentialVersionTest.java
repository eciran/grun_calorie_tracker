package com.grun.calorietracker.security;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtCredentialVersionTest {
    private static final byte[] KEY = "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8);
    private final UserRepository users = mock(UserRepository.class);
    private final JwtUtil jwt = new JwtUtil();
    private UserEntity user;

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(jwt, "users", users);
        ReflectionTestUtils.setField(jwt, "secretKey", Base64.getEncoder().encodeToString(KEY));
        ReflectionTestUtils.setField(jwt, "expirationTime", 900_000L);
        jwt.init();
        user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(users.findAuthTokenVersionById(user.getId())).thenAnswer(invocation -> Optional.of(user.getAuthTokenVersion()));
    }

    @Test void revocationInvalidatesEveryJwtPurposeAndFreshTokensRemainUsable() {
        String reauth = jwt.generateAdminReauthenticationToken(user.getEmail());
        List<String> tokens = List.of(jwt.generateUserToken(user),
                jwt.generateAdminMfaEnrollmentToken(user.getEmail()), reauth,
                jwt.generateAdminSessionToken(user.getEmail(), "sid", Instant.now().plusSeconds(600)));
        tokens.forEach(token -> assertThat(jwt.isTokenValid(token, user.getEmail())).isTrue());
        assertThat(jwt.isAdminReauthenticationTokenValid(reauth, user.getEmail())).isTrue();
        user.setAuthTokenVersion(1);
        tokens.forEach(token -> assertThat(jwt.isTokenValid(token, user.getEmail())).isFalse());
        assertThat(jwt.isAdminReauthenticationTokenValid(reauth, user.getEmail())).isFalse();
        assertThat(jwt.isTokenValid(jwt.generateUserToken(user), user.getEmail())).isTrue();
        assertThat(jwt.isTokenValid(jwt.generateAdminMfaEnrollmentToken(user.getEmail()), user.getEmail())).isTrue();
        assertThat(jwt.isAdminReauthenticationTokenValid(jwt.generateAdminReauthenticationToken(user.getEmail()), user.getEmail())).isTrue();
    }

    @Test void legacyTokensOnlyWorkBeforeFirstRevocation() {
        String legacy = signed(null);
        assertThat(jwt.isTokenValid(legacy, user.getEmail())).isTrue();
        user.setAuthTokenVersion(1);
        assertThat(jwt.isTokenValid(legacy, user.getEmail())).isFalse();
    }

    @Test void malformedVersionCannotBypassRevocation() {
        for (Object version : List.of("0", 0.5, -1L, 9L)) {
            assertThat(jwt.isTokenValid(signed(version), user.getEmail())).isFalse();
        }
    }

    @Test void deletedUserAndSubjectMismatchAreRejected() {
        String token = jwt.generateUserToken(user);
        assertThat(jwt.isTokenValid(token, "someone@example.com")).isFalse();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.empty());
        assertThat(jwt.isTokenValid(token, user.getEmail())).isFalse();
    }

    @Test void issuanceFromPreRevocationSnapshotCannotAcquireNewVersion() {
        UserEntity stale = new UserEntity();
        stale.setEmail(user.getEmail());
        user.setAuthTokenVersion(1);
        assertThat(jwt.isTokenValid(jwt.generateUserToken(stale), user.getEmail())).isFalse();
    }

    @Test void internalVersionIsNotSerializedAsUserData() {
        user.setAuthTokenVersion(9);
        var json = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(user);
        assertThat(json.has("authTokenVersion")).isFalse();
    }

    private String signed(Object version) {
        var builder = Jwts.builder().setSubject(user.getEmail()).setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000));
        if (version != null) builder.claim("authVersion", version);
        return builder.signWith(Keys.hmacShaKeyFor(KEY), SignatureAlgorithm.HS256).compact();
    }
}
