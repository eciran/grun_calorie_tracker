package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Links an existing user-owned diary record as the replacement for a planned item.")
public class MealPlanItemReplaceRequestDto {
    private Long foodLogId;
    private Long recipeLogId;
}
