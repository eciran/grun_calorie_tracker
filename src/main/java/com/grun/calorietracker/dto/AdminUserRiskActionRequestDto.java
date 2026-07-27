package com.grun.calorietracker.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserRiskActionRequestDto(
        @NotBlank
        @Size(max = 500)
        String reason,

        @AssertTrue(message = "Explicit confirmation is required.")
        boolean confirmed
) {
}
