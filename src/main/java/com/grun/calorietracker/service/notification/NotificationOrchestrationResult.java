package com.grun.calorietracker.service.notification;

import com.grun.calorietracker.enums.NotificationOccurrenceStatus;

public record NotificationOrchestrationResult(
        Long occurrenceId,
        Long notificationId,
        Long outboxId,
        NotificationOccurrenceStatus status,
        String reasonCode,
        boolean duplicate
) { }
