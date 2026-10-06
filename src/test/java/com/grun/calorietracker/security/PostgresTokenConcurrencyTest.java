package com.grun.calorietracker.security;

import com.grun.calorietracker.dto.PasswordResetConfirmRequestDto;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named = "grun.security.pg", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:55433/security_test",
        "spring.datasource.username=postgres", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.generate-unique-name=false",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "grun.rate-limit.enabled=false"
})
class PostgresTokenConcurrencyTest {
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository tokens;
    @Autowired PasswordResetTokenRepository resets;
    @Autowired AdminMfaRecoveryCodeRepository recovery;
    @Autowired RefreshTokenService refresh;
    @Autowired PasswordResetService reset;
    @Autowired AdminMfaService mfa;
    @Autowired PasswordEncoder passwords;
    @org.springframework.boot.test.mock.mockito.SpyBean JwtUtil jwt;
    @Autowired PlatformTransactionManager transactions;
    @MockBean PasswordResetMailSender resetMail;
    @MockBean MailDeliveryService mail;
    @MockBean AdminAuditService audit;
    @MockBean UserActivityService activity;
    @MockBean TotpService totp;
    @MockBean AdminMfaSecretCipher cipher;
    @MockBean AdminSecurityAlertService alerts;

    @Test void concurrentRefreshMintsExactlyOneReplacementAndDoesNotRevokeWinner() throws Exception {
        UserEntity user = user();
        String raw = refresh.createRefreshToken(user);
        var outcomes = race(4, ignored -> refresh.refreshAccessToken(raw));
        assertThat(outcomes.stream().filter(o -> o.value != null)).hasSize(1);
        assertThat(outcomes.stream().filter(o -> o.failure != null).map(o -> o.failure))
                .allMatch(e -> e instanceof com.grun.calorietracker.exception.RequestConflictException);
        var winner = outcomes.stream().filter(o -> o.value != null).findFirst().orElseThrow().value;
        assertThat(jwt.isTokenValid(winner.getToken(), user.getEmail())).isTrue();
        assertThat(users.findAuthTokenVersionById(user.getId())).contains(0L);
        assertThat(tokens.findByUserAndRevokedAtIsNullAndUsedAtIsNull(user)).hasSize(1);
        assertThat(refresh.refreshAccessToken(winner.getRefreshToken()).getToken()).isNotBlank();
    }

    @Test void refreshFailureRollsBackConsumptionAndReplacement() throws Exception {
        UserEntity user = user();
        String raw = refresh.createRefreshToken(user);
        doThrow(new IllegalArgumentException("Synthetic JWT failure")).when(jwt)
                .generateUserToken(argThat(account -> account != null && user.getId().equals(account.getId())));
        assertThatThrownBy(() -> refresh.refreshAccessToken(raw)).isInstanceOf(IllegalArgumentException.class);
        assertThat(tokens.findByUserAndRevokedAtIsNullAndUsedAtIsNull(user)).hasSize(1);
        assertThat(tokens.findByTokenHashAndRevokedAtIsNullAndUsedAtIsNull(hash(raw))).isPresent();
        org.mockito.Mockito.reset(jwt);
        assertThat(refresh.refreshAccessToken(raw).getToken()).isNotBlank();
    }

    @Test void sameResetTokenCannotChangePasswordTwice() throws Exception {
        UserEntity user = user();
        String raw = resetToken(user);
        var results = race(2, i -> reset.confirmPasswordReset(resetRequest(raw, "NewStrongPass" + i + "!")));
        assertThat(results.stream().filter(o -> o.value != null)).hasSize(1);
        assertThat(results.stream().filter(o -> o.failure != null).map(o -> o.failure))
                .allMatch(e -> e instanceof IllegalArgumentException);
        UserEntity persisted = users.findById(user.getId()).orElseThrow();
        assertThat(persisted.getAuthTokenVersion()).isEqualTo(1L);
        assertThat(passwords.matches("NewStrongPass0!", persisted.getPassword())
                || passwords.matches("NewStrongPass1!", persisted.getPassword())).isTrue();
        assertThat(resets.findByTokenHashAndUsedAtIsNull(hash(raw))).isEmpty();
    }

    @Test void refreshAndSingleTokenLogoutCannotOverwriteEachOther() throws Exception {
        UserEntity user = user();
        String raw = refresh.createRefreshToken(user);
        var results = race(2, i -> {
            if (i == 0) return refresh.refreshAccessToken(raw);
            refresh.revokeRefreshToken(raw);
            return null;
        });
        assertThat(results.stream().filter(o -> o.failure != null)).hasSize(1);
        RefreshTokenEntity persisted = tokens.findByTokenHash(hash(raw)).orElseThrow();
        assertThat(persisted.getUsedAt() != null || persisted.getRevokedAt() != null).isTrue();
        assertThat(tokens.findByTokenHashAndRevokedAtIsNullAndUsedAtIsNull(hash(raw))).isEmpty();
        assertThat(users.findAuthTokenVersionById(user.getId())).contains(0L);
    }

    @Test void globalLogoutWinsAgainstConcurrentRefresh() throws Exception {
        UserEntity user = user();
        String raw = refresh.createRefreshToken(user);
        var results = race(2, i -> {
            if (i == 0) return refresh.refreshAccessToken(raw);
            refresh.revokeAllForUser(users.findById(user.getId()).orElseThrow());
            return null;
        });
        assertThat(users.findAuthTokenVersionById(user.getId())).contains(1L);
        assertThat(tokens.findByUserAndRevokedAtIsNullAndUsedAtIsNull(user)).isEmpty();
        results.stream().filter(o -> o.value != null).forEach(o ->
                assertThat(jwt.isTokenValid(o.value.getToken(), user.getEmail())).isFalse());
    }

    @Test void resetFailureRollsBackTokenConsumptionPasswordAndRevocationTogether() throws Exception {
        UserEntity user = user();
        user.setRole(UserRole.OWNER);
        users.saveAndFlush(user);
        String raw = resetToken(user);
        doThrow(new IllegalStateException("Synthetic audit failure")).when(audit)
                .record(anyString(), any(), any(), anyString(), any(), any(), any());
        assertThatThrownBy(() -> reset.confirmPasswordReset(resetRequest(raw, "NewStrongPass1!")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(resets.findByTokenHashAndUsedAtIsNull(hash(raw))).isPresent();
        UserEntity persisted = users.findById(user.getId()).orElseThrow();
        assertThat(persisted.getAuthTokenVersion()).isZero();
        assertThat(passwords.matches("OldStrongPass1!", persisted.getPassword())).isTrue();
    }

    @Test void oneRecoveryCodeCannotAuthorizeTwoConcurrentLogins() throws Exception {
        UserEntity user = user();
        user.setRole(UserRole.OWNER);
        user.setAdminMfaEnabled(true);
        user.setAdminMfaSecretEncrypted("synthetic-secret");
        users.saveAndFlush(user);
        var code = new AdminMfaRecoveryCodeEntity();
        code.setUser(user);
        code.setCodeHash(passwords.encode("AAAABBBBCCCC"));
        recovery.saveAndFlush(code);
        var outcomes = race(2, ignored -> { mfa.verifyLogin(user, "AAAA-BBBB-CCCC"); return true; });
        assertThat(outcomes.stream().filter(o -> o.value != null)).hasSize(1);
        assertThat(outcomes.stream().filter(o -> o.failure != null)).hasSize(1);
        assertThat(recovery.countByUserAndUsedAtIsNull(user)).isZero();
        assertThat(recovery.findById(code.getId()).orElseThrow().getUsedAt()).isNotNull();
    }

    private UserEntity user() {
        return new TransactionTemplate(transactions).execute(status -> {
            UserEntity user = new UserEntity();
            user.setEmail("pg-race-" + UUID.randomUUID() + "@example.com");
            user.setName("Synthetic security test");
            user.setRole(UserRole.STANDARD);
            user.setPassword(passwords.encode("OldStrongPass1!"));
            return users.saveAndFlush(user);
        });
    }

    private String resetToken(UserEntity user) throws Exception {
        String raw = UUID.randomUUID().toString();
        PasswordResetTokenEntity token = new PasswordResetTokenEntity();
        token.setUser(user);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        resets.saveAndFlush(token);
        return raw;
    }

    private PasswordResetConfirmRequestDto resetRequest(String raw, String password) {
        var request = new PasswordResetConfirmRequestDto();
        request.setToken(raw);
        request.setNewPassword(password);
        return request;
    }

    private String hash(String raw) throws Exception {
        return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    }

    private <T> List<Outcome<T>> race(int count, IntFunction<T> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome<T>>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race start timed out");
                    try { return new Outcome<>(action.apply(index), null); }
                    catch (RuntimeException failure) { return new Outcome<>(null, failure); }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Outcome<T>> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private record Outcome<T>(T value, RuntimeException failure) {}
}
