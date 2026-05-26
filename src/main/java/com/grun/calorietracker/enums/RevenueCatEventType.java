package com.grun.calorietracker.enums;

public enum RevenueCatEventType {
    INITIAL_PURCHASE,
    RENEWAL,
    CANCELLATION,
    UNCANCELLATION,
    EXPIRATION,
    BILLING_ISSUE,
    PRODUCT_CHANGE,
    NON_RENEWING_PURCHASE,
    SUBSCRIPTION_PAUSED,
    TRANSFER,
    UNKNOWN;

    public static RevenueCatEventType from(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return RevenueCatEventType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }
}
