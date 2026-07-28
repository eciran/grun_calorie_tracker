package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FastingScheduleExceptionType;
import jakarta.validation.constraints.NotNull;
import java.time.*;

public record FastingScheduleExceptionRequestDto(
        @NotNull FastingScheduleExceptionType type,
        LocalDate targetDate,
        LocalTime plannedStartTime) {}