package com.grun.calorietracker.enums;

public enum NotificationOutboxStatus {
    PENDING,
    PROCESSING,
    RETRY,
    COMPLETED,
    FAILED_FINAL,
    UNKNOWN,
    EXPIRED,
    CANCELLED
}
