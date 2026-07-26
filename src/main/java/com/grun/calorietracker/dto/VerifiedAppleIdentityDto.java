package com.grun.calorietracker.dto;

import java.time.Instant;

public record VerifiedAppleIdentityDto(
        String subject,
        String email,
        boolean emailVerified,
        Instant issuedAt
) {
    public VerifiedAppleIdentityDto(String subject, String email, boolean emailVerified) {
        this(subject, email, emailVerified, null);
    }
}
