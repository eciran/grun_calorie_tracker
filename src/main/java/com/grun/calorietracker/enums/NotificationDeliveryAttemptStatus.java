package com.grun.calorietracker.enums;

public enum NotificationDeliveryAttemptStatus {
    PENDING,
    PROCESSING,
    PROVIDER_ACCEPTED,
    DELIVERED,
    FAILED_RETRYABLE,
    FAILED_FINAL,
    INVALID_TOKEN,
    UNKNOWN,
    SUPPRESSED
}
