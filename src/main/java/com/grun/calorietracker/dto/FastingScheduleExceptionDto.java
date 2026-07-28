package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FastingScheduleExceptionType;
import java.time.*;

public record FastingScheduleExceptionDto(
        Long id, LocalDate sourceDate, LocalDate targetDate,
        FastingScheduleExceptionType type, LocalTime plannedStartTime,
        LocalDateTime createdAt, LocalDateTime updatedAt) {}