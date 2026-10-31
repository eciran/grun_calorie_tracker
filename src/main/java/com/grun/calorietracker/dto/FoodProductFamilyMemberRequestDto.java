package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FoodProductFamilyMemberRequestDto(
        @NotNull Long productId,
        @NotNull @Positive Double packageQuantity,
        @NotNull FoodPortionUnit packageUnit,
        @NotBlank String packageLabel,
        boolean defaultVariant
) {
}
