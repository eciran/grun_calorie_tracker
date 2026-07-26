package com.grun.calorietracker.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AdminCustomer360Dto(
        ProfileSummary profile,
        SubscriptionSummary subscription,
        AiSummary ai,
        NotificationSummary notifications,
        SecuritySummary security,
        ConsentSummary consent,
        ActivitySummary activity,
        List<AdminUserSupportNoteDto> supportNotes
) {
    public record ProfileSummary(
            Long id, String email, String name, String role,
            boolean emailVerified, boolean passwordSet,
            String marketRegion, String preferredLanguage,
            boolean accountEnabled, boolean accountLocked,
            Instant createdAt, Instant emailVerifiedAt,
            Instant lastLoginAt, Instant lastActiveAt
    ) {
    }

    public record SubscriptionSummary(
            String plan,
            String status,
            String billingPeriod,
            LocalDate startDate,
            LocalDate endDate,
            boolean autoRenew,
            int aiMonthlyQuota,
            int aiUsedThisPeriod,
            int aiAddonRemaining,
            LocalDate aiAddonExpiresAt,
            List<String> activeFeatures
    ) {
    }

    public record AiSummary(
            long totalRequests,
            LocalDateTime lastRequestAt,
            Map<String, Long> recentStatusCounts,
            Map<String, Long> recentRequestTypeCounts,
            int recentSampleSize
    ) {
    }

    public record NotificationSummary(
            long total,
            long unread,
            List<NotificationItem> recent
    ) {
    }

    public record NotificationItem(
            Long id,
            String type,
            String severity,
            String source,
            boolean read,
            LocalDateTime createdAt
    ) {
    }

    public record SecuritySummary(
            int activeSessions,
            List<SecurityEvent> recentEvents
    ) {
    }

    public record SecurityEvent(
            Long id,
            String eventType,
            String provider,
            String resultCode,
            LocalDateTime createdAt
    ) {
    }

    public record ConsentSummary(
            long total,
            List<ConsentItem> recent
    ) {
    }

    public record ConsentItem(
            Long id,
            String consentType,
            String version,
            String status,
            String source,
            LocalDateTime createdAt
    ) {
    }

    public record ActivitySummary(
            long foodLogCount,
            LocalDateTime lastFoodLogAt,
            long productEventCount,
            List<ActivityEvent> recentProductEvents
    ) {
    }

    public record ActivityEvent(
            Long id,
            String eventType,
            String surface,
            LocalDateTime createdAt
    ) {
    }
}
