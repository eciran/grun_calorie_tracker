package com.grun.calorietracker.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum NotificationEventType {
    AI_REQUEST_READY("ai_request_ready", NotificationClassification.USER_REQUESTED_RESULT),
    AI_REQUEST_FAILED("ai_request_failed", NotificationClassification.USER_REQUESTED_RESULT),
    AI_QUOTA_REFUND_APPROVED("ai_quota_refund_approved", NotificationClassification.TRANSACTIONAL_ACCOUNT),
    AI_QUOTA_REFUND_REJECTED("ai_quota_refund_rejected", NotificationClassification.TRANSACTIONAL_ACCOUNT),
    RECIPE_REVIEW_APPROVED("recipe_review_approved", NotificationClassification.USER_REQUESTED_RESULT),
    RECIPE_REVIEW_REJECTED("recipe_review_rejected", NotificationClassification.USER_REQUESTED_RESULT),
    PRODUCT_INTAKE_UPDATED("product_intake", NotificationClassification.USER_REQUESTED_RESULT),
    FASTING_REMINDER_DUE("fasting_reminder", NotificationClassification.BEHAVIOR_REMINDER),
    STEP_REMINDER_DUE("step_reminder", NotificationClassification.BEHAVIOR_REMINDER),
    WATER_REMINDER_DUE("water_reminder", NotificationClassification.BEHAVIOR_REMINDER),
    MEAL_REMINDER_BREAKFAST("meal_reminder_breakfast", NotificationClassification.BEHAVIOR_REMINDER),
    MEAL_REMINDER_LUNCH("meal_reminder_lunch", NotificationClassification.BEHAVIOR_REMINDER),
    MEAL_REMINDER_DINNER("meal_reminder_dinner", NotificationClassification.BEHAVIOR_REMINDER),
    MEAL_REMINDER_DINNER_KCAL("meal_reminder_dinner_kcal", NotificationClassification.BEHAVIOR_REMINDER,
            "remainingKcal"),
    MEAL_REMINDER_DAILY_CATCHUP("meal_reminder_daily_catchup", NotificationClassification.BEHAVIOR_REMINDER),
    SUBSCRIPTION_STARTED("subscription_started", NotificationClassification.TRANSACTIONAL_ACCOUNT,
            "planName", "periodEndDate"),
    SUBSCRIPTION_RENEWED("subscription_renewed", NotificationClassification.TRANSACTIONAL_ACCOUNT,
            "planName", "periodEndDate"),
    SUBSCRIPTION_CANCELLED("subscription_cancelled", NotificationClassification.TRANSACTIONAL_ACCOUNT,
            "planName", "accessUntilDate"),
    SUBSCRIPTION_RESUMED("subscription_resumed", NotificationClassification.TRANSACTIONAL_ACCOUNT,
            "planName", "periodEndDate"),
    SUBSCRIPTION_BILLING_ISSUE("subscription_billing_issue", NotificationClassification.TRANSACTIONAL_ACCOUNT,
            "planName", "accessUntilDate"),
    SUBSCRIPTION_EXPIRED("subscription_expired", NotificationClassification.TRANSACTIONAL_ACCOUNT,
            "planName", "expiredAt"),
    SUBSCRIPTION_PLAN_CHANGED("subscription_plan_changed", NotificationClassification.TRANSACTIONAL_ACCOUNT,
            "planName", "effectiveDate"),
    SUBSCRIPTION_PAUSED("subscription_paused", NotificationClassification.TRANSACTIONAL_ACCOUNT,
            "planName", "effectiveDate"),
    SUBSCRIPTION_REFUNDED("subscription_refunded", NotificationClassification.TRANSACTIONAL_ACCOUNT,
            "planName", "effectiveDate"),
    AI_ADDON_PURCHASED("ai_addon_purchased", NotificationClassification.TRANSACTIONAL_ACCOUNT,
            "creditAmount", "validUntilDate"),
    SYSTEM_ANNOUNCEMENT("system_announcement", NotificationClassification.TRANSACTIONAL_ACCOUNT),
    MARKETING_CAMPAIGN("marketing", NotificationClassification.MARKETING),
    AI_REJECTION_ALERT("ai_rejection_alert", NotificationClassification.INTERNAL_OPERATIONAL),
    SYSTEM_ALERT("system_alert", NotificationClassification.INTERNAL_OPERATIONAL),
    SUBSCRIPTION_PROVIDER_ALERT("subscription_provider_alert", NotificationClassification.INTERNAL_OPERATIONAL),
    ADMIN_SECURITY_ALERT("admin_security_alert", NotificationClassification.INTERNAL_OPERATIONAL);

    private final String definitionKey;
    private final NotificationClassification classification;
    private final Set<String> allowedParameters;

    NotificationEventType(String definitionKey, NotificationClassification classification, String... allowedParameters) {
        this.definitionKey = definitionKey;
        this.classification = classification;
        this.allowedParameters = Set.of(allowedParameters);
    }

    public String definitionKey() {
        return definitionKey;
    }

    public NotificationClassification classification() {
        return classification;
    }

    public Set<String> allowedParameters() {
        return Collections.unmodifiableSet(allowedParameters);
    }

    public Set<String> requiredParameters() {
        return switch (this) {
            case SUBSCRIPTION_STARTED, SUBSCRIPTION_BILLING_ISSUE -> Set.of("planName");
            case SUBSCRIPTION_RENEWED, SUBSCRIPTION_RESUMED -> Set.of("planName", "periodEndDate");
            case SUBSCRIPTION_CANCELLED -> Set.of("planName", "accessUntilDate");
            case SUBSCRIPTION_EXPIRED -> Set.of("planName", "expiredAt");
            case SUBSCRIPTION_PLAN_CHANGED, SUBSCRIPTION_PAUSED, SUBSCRIPTION_REFUNDED ->
                    Set.of("planName", "effectiveDate");
            case AI_ADDON_PURCHASED -> Set.of("creditAmount", "validUntilDate");
            case MEAL_REMINDER_DINNER_KCAL -> Set.of("remainingKcal");
            default -> Set.of();
        };
    }

    public Set<NotificationDeliveryChannel> defaultChannels() {
        return EnumSet.of(NotificationDeliveryChannel.IN_APP, NotificationDeliveryChannel.PUSH);
    }
}
