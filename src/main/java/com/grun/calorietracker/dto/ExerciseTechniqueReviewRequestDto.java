package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ExerciseTechniqueReviewStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExerciseTechniqueReviewRequestDto(
        @NotNull ExerciseTechniqueReviewStatus status,
        @NotBlank @Size(max = 1000) String note
) {
}
