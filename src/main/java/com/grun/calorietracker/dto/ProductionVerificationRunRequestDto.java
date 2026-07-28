package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ProductionVerificationStatus;
import jakarta.validation.constraints.*;

import java.time.Instant;

public record ProductionVerificationRunRequestDto(
        @NotBlank @Pattern(regexp = "REVENUECAT|BREVO|PUSH|DATABASE|CLOUD") String provider,
        @NotBlank @Pattern(regexp = "SANDBOX|STAGING|PRODUCTION") String environment,
        @NotBlank @Pattern(regexp = "[A-Z0-9_]{3,64}") String scenario,
        @NotNull ProductionVerificationStatus status,
        @NotBlank @Size(max = 240) String evidenceReference,
        @NotBlank @Size(max = 500) String summary,
        Instant validUntil
) {
}
