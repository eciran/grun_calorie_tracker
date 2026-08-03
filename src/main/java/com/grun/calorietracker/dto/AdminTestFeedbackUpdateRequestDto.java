package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.TestFeedbackStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminTestFeedbackUpdateRequestDto(
        @NotNull TestFeedbackStatus status,
        @Size(max = 2000) String internalNote
) { }
