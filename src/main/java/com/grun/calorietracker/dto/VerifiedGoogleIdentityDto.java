package com.grun.calorietracker.dto;

import java.time.Instant;

public record VerifiedGoogleIdentityDto(
        String subject,
        String email,
        String name,
        boolean emailVerified,
        Instant issuedAt
) {
    public VerifiedGoogleIdentityDto(String subject, String email, String name, boolean emailVerified) {
        this(subject, email, name, emailVerified, null);
    }
}
