package com.grun.calorietracker.dto;

public record VerifiedGoogleIdentityDto(
        String subject,
        String email,
        String name,
        boolean emailVerified
) {
}
