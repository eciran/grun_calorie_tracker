package com.grun.calorietracker.service.notification;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.NotificationDeliveryChannel;
import com.grun.calorietracker.enums.NotificationEventType;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record NotificationOrchestrationRequest(
        UserEntity user,
        NotificationEventType eventType,
        String source,
        String sourceEventId,
        String title,
        String message,
        String severity,
        String targetType,
        String targetId,
        String targetRoute,
        String primaryAction,
        Map<String, String> parameters,
        Set<NotificationDeliveryChannel> requestedChannels,
        Instant eligibleAt,
        Instant expiresAt,
        Integer actionAmountMl
) {
    public NotificationOrchestrationRequest(
            UserEntity user,
            NotificationEventType eventType,
            String source,
            String sourceEventId,
            String title,
            String message,
            String severity,
            String targetType,
            String targetId,
            String targetRoute,
            String primaryAction,
            Map<String, String> parameters,
            Set<NotificationDeliveryChannel> requestedChannels,
            Instant eligibleAt,
            Instant expiresAt
    ) {
        this(user, eventType, source, sourceEventId, title, message, severity, targetType, targetId,
                targetRoute, primaryAction, parameters, requestedChannels, eligibleAt, expiresAt, null);
    }
}
