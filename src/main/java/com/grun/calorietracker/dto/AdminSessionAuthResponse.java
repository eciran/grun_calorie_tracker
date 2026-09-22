package com.grun.calorietracker.dto;

import java.time.Instant;

/** Admin-only deadlines; no refresh secret or session cookie is exposed. */
public class AdminSessionAuthResponse extends AuthResponse {
    private final SessionState adminSession;

    public AdminSessionAuthResponse(String token, long expiresIn, String message, SessionState adminSession) {
        super(token, null, "Bearer", expiresIn, message);
        this.adminSession = adminSession;
    }

    public SessionState getAdminSession() { return adminSession; }

    public record SessionState(String sessionId, Instant serverTime, Instant idleExpiresAt,
                               Instant absoluteExpiresAt, long idleTimeoutMs, Instant tokenExpiresAt) {}
}
