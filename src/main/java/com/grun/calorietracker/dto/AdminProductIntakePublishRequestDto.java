package com.grun.calorietracker.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminProductIntakePublishRequestDto(
        @NotBlank @Size(max = 1000) String note,
        @AssertTrue(message = "Explicit publication confirmation is required.") boolean confirmed
) {
}