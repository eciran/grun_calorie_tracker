package com.grun.calorietracker.dto;

public record VerifiedAppleIdentityDto(
        String subject,
        String email,
        boolean emailVerified
) {
}
