package com.grun.calorietracker.security;

import com.grun.calorietracker.exception.AdminAuthenticationRateLimitException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class AdminAuthenticationAttemptGuard {
    private final RequestRateLimiter rateLimiter;

    @Value("${grun.rate-limit.enabled:true}")
    private boolean enabled = true;
    @Value("${grun.rate-limit.admin-auth.account.max-requests-per-minute:5}")
    private int perMinute = 5;
    @Value("${grun.rate-limit.admin-auth.account.max-requests-per-fifteen-minutes:20}")
    private int perFifteenMinutes = 20;

    public void requireAllowed(String email) {
        if (!enabled) return;
        String account = accountKey(email);
        // Shared across login, enrollment and reauthentication; no credential data in limiter logs.
        if (!rateLimiter.isAllowed("admin-auth:account:15m:" + account, perFifteenMinutes, 900_000)) {
            throw new AdminAuthenticationRateLimitException(900);
        }
        if (!rateLimiter.isAllowed("admin-auth:account:1m:" + account, perMinute, 60_000)) {
            throw new AdminAuthenticationRateLimitException(60);
        }
    }

    private String accountKey(String email) {
        try {
            String normalized = email.trim().toLowerCase(Locale.ROOT);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
