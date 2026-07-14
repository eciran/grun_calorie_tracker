package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Admin assessment of one canonical duplicate candidate.")
public class FoodCanonicalCandidateAssessmentDto {

    private FoodProductDto product;

    @Schema(description = "Whether this product currently passes canonical-primary safety rules.")
    private Boolean primaryEligible;

    @Schema(description = "Deterministic reasons that prevent primary selection.")
    private List<String> eligibilityIssues;

    @Schema(description = "Whether this is the highest-ranked currently eligible candidate.")
    private Boolean recommended;
}