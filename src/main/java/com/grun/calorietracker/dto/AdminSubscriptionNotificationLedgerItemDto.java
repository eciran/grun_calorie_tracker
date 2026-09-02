package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.*;
import java.time.Instant;

public record AdminSubscriptionNotificationLedgerItemDto(
        Long occurrenceId, Long userId, NotificationEventType eventType, String definitionKey,
        String source, NotificationOccurrenceStatus occurrenceStatus, String reasonCode,
        Long notificationId, Long outboxId, NotificationOutboxStatus outboxStatus,
        int dispatchCount, String lastErrorCode, Instant createdAt, Instant updatedAt) { }
