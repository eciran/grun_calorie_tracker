package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserNotificationRequestDto(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 500) String message,
        @NotBlank String category,
        @NotBlank String severity,
        @NotBlank String delivery,
        String targetRoute,
        String primaryAction
) {
}
