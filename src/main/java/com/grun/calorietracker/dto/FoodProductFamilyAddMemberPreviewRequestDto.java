package com.grun.calorietracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FoodProductFamilyAddMemberPreviewRequestDto(
        @NotNull @Valid FoodProductFamilyMemberRequestDto member,
        @NotBlank @Size(min = 10, max = 1000) String reason
) {
}
