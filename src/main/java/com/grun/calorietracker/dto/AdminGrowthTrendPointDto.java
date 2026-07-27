package com.grun.calorietracker.dto;

import java.time.LocalDate;

public record AdminGrowthTrendPointDto(
        LocalDate date,
        long registrations,
        long activeUsers
) {
}
