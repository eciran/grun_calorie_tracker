package com.grun.calorietracker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CatalogReviewAssignmentRequestDto(
        @Email @Size(max = 255) String assignee,
        @NotNull LocalDateTime dueAt,
        @NotBlank @Size(max = 500) String reason
) {
}
