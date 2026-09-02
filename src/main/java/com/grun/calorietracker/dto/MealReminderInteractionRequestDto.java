package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MealReminderInteractionRequestDto(
        @NotBlank @Size(max = 100) String eventId,
        @NotBlank @Pattern(regexp = "FOREGROUND|BACKGROUND|COLD_START|LOGIN_RESUME|IN_APP") String source
) { }
