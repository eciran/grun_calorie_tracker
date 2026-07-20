package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MealPlanMealLogRequestDto {

    @NotNull
    private LocalDate planDate;

    @NotBlank
    private String mealType;

    @NotNull
    private LocalDateTime loggedAt;
}

