package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminProductIntakeActionRequestDto(
        @NotBlank @Size(max = 1000) String note
) {
}