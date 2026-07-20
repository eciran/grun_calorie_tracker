package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Actual amount consumed from one active meal-plan item.")
public class MealPlanItemLogRequestDto {

    @NotNull
    @Positive
    private Double consumedQuantity;

    @NotNull
    private FoodPortionUnit consumedUnit;

    @NotNull
    private LocalDateTime loggedAt;
}
