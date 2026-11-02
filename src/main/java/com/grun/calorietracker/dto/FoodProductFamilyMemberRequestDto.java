package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;

public record FoodProductFamilyMemberRequestDto(
        @NotNull Long productId,
        @NotNull @Positive @Digits(integer = 9, fraction = 3) Double packageQuantity,
        @NotNull FoodPortionUnit packageUnit,
        @NotBlank @Size(max = 120) String packageLabel,
        boolean defaultVariant
) {
}
