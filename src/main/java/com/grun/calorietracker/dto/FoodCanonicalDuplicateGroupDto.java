package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodCanonicalResolutionState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Potential duplicate generic products sharing one canonical food identity.")
public class FoodCanonicalDuplicateGroupDto {

    @Schema(description = "Canonical identity shared by products from different source records.", example = "GLOBAL:GENERIC_INGREDIENT:RAW:banana")
    private String canonicalFoodKey;

    @Schema(description = "Number of products in this candidate group.", example = "2")
    private Integer productCount;

    @Schema(description = "Products ordered by quality score and usage for admin comparison.")
    private List<FoodProductDto> products;

    @Schema(description = "Deterministic eligibility assessment for each product candidate.")
    private List<FoodCanonicalCandidateAssessmentDto> candidateAssessments;

    @Schema(description = "Whether the stored canonical decision is currently safe and active.", example = "true")
    private Boolean resolved;

    @Schema(description = "Current operational state of the canonical decision.", example = "NEEDS_REVIEW")
    private FoodCanonicalResolutionState resolutionState;

    @Schema(description = "Reason the group needs attention, when applicable.")
    private String resolutionStatusReason;

    @Schema(description = "Selected primary product id when a decision record exists.", example = "1001")
    private Long primaryProductId;

    @Schema(description = "Highest-ranked currently eligible candidate; admin confirmation is still required.", example = "1001")
    private Long recommendedPrimaryProductId;

    @Schema(description = "Admin that last selected the primary product.", example = "admin@grun.app")
    private String resolvedBy;

    @Schema(description = "ISO timestamp of the latest resolution decision.", example = "2026-07-13T22:00:00")
    private String resolvedAt;

    public FoodCanonicalDuplicateGroupDto(
            String canonicalFoodKey,
            Integer productCount,
            List<FoodProductDto> products,
            Boolean resolved,
            Long primaryProductId,
            String resolvedBy,
            String resolvedAt
    ) {
        this(
                canonicalFoodKey,
                productCount,
                products,
                List.of(),
                resolved,
                Boolean.TRUE.equals(resolved)
                        ? FoodCanonicalResolutionState.RESOLVED
                        : FoodCanonicalResolutionState.UNRESOLVED,
                Boolean.TRUE.equals(resolved) ? null : "No canonical primary has been selected.",
                primaryProductId,
                primaryProductId,
                resolvedBy,
                resolvedAt
        );
    }
    public FoodCanonicalDuplicateGroupDto(
            String canonicalFoodKey,
            Integer productCount,
            List<FoodProductDto> products
    ) {
        this(
                canonicalFoodKey,
                productCount,
                products,
                List.of(),
                false,
                FoodCanonicalResolutionState.UNRESOLVED,
                "No canonical primary has been selected.",
                null,
                null,
                null,
                null
        );
    }
}