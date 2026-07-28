package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ProductionVerificationStatus;
import java.time.Instant;

public record ProductionVerificationRunDto(Long id, String provider, String environment, String scenario,
        ProductionVerificationStatus status, String evidenceReference, String summary, String executedBy,
        Instant executedAt, Instant validUntil, boolean expired) {
}
