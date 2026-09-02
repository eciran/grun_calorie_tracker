package com.grun.calorietracker.dto;

import java.time.LocalDate;

public record MealReminderNotificationContextDto(
        Long notificationId,
        Long occurrenceId,
        LocalDate localDate,
        String destination,
        String mealType,
        String fallbackRoute,
        boolean staleFallback,
        int contextVersion
) { }
