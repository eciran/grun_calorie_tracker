package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Micronutrient field coverage and target-profile applicability for a nutrition summary.")
public class MicronutrientDataQualityDto {

    @Schema(description = "Number of tracked micronutrients with a recorded aggregate value.", example = "8")
    private Integer availableNutrientCount;

    @Schema(description = "Number of core micronutrients evaluated for coverage.", example = "11")
    private Integer trackedNutrientCount;

    @Schema(description = "Available core micronutrient fields as a percentage of tracked fields.", example = "72.73")
    private Double coveragePercent;

    @Schema(description = "Coverage classification: NONE, PARTIAL, or COMPLETE.", example = "PARTIAL")
    private String coverageLevel;

    @Schema(description = "Core nutrient codes whose aggregate values were unavailable.")
    private List<String> missingNutrients;

    @Schema(description = "Backend-owned reference profile code.", example = "EU_ADULT_DIETARY_REFERENCE_V1")
    private String targetProfileCode;

    @Schema(description = "Whether the selected reference profile is applicable to this user.", example = "true")
    private Boolean targetProfileApplicable;

    @Schema(description = "Reason code when targets cannot be safely applied.", example = "ADULT_PROFILE_NOT_APPLICABLE")
    private String targetProfileUnavailableReason;

    @Schema(description = "Authoritative reference identifiers used by the target profile.")
    private List<String> referenceSources;
}