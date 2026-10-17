package com.grun.calorietracker.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

/** Explicit source-only v2 approval, never an approval to update product nutrition. */
public record TypedSourceObservationRequest(
        @NotNull @Pattern(regexp = "typed-source-observation-r5-v2") String contractVersion,
        @NotNull @Pattern(regexp = "USDA_FOODDATA|OPEN_FOOD_FACTS") String provider,
        @NotNull @Pattern(regexp = "potassium|vitamin_e|vitamin_b12|sodium|fiber") String field,
        @NotBlank @Size(max = 1000000) String sourceJson,
        @NotNull @Pattern(regexp = "[a-f0-9]{64}") String sourceSha256,
        @NotNull Instant observedAt,
        @Pattern(regexp = "[a-f0-9]{64}") String expectedCurrentFingerprint,
        boolean approved,
        @Size(max = 1000) String approvalReason) {
    public TypedNutrientEvidenceRequest evidenceRequest() {
        return new TypedNutrientEvidenceRequest(field, sourceJson, sourceSha256, observedAt,
                expectedCurrentFingerprint, approved, approvalReason);
    }
}
