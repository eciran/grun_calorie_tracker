package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record TypedNutrientEvidenceRequest(
        @Pattern(regexp = "vitamin_a|vitamin_d|sugar") @NotNull String field,
        @NotBlank @Size(max = 1000000) String sourceJson,
        @NotNull @Pattern(regexp = "[a-f0-9]{64}") String sourceSha256,
        @NotNull Instant observedAt,
        @Pattern(regexp = "[a-f0-9]{64}") String expectedCurrentFingerprint,
        boolean approved,
        @Size(max = 1000) String approvalReason) {
}
