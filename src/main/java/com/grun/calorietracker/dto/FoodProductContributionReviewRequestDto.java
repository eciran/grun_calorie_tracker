package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodProductContributionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Admin decision for a pending food-label contribution.")
public class FoodProductContributionReviewRequestDto {
    @NotNull
    private FoodProductContributionStatus decision;

    @Size(max = 1000)
    private String reviewNote;
}
