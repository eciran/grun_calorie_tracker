package com.grun.calorietracker.enums;

import java.util.Arrays;

public enum ApiErrorCode {
    USER_NOT_FOUND("error.user.not-found"),
    INVALID_CREDENTIALS("error.invalid.credentials"),
    ACCESS_DENIED("error.access.denied"),
    SUBSCRIPTION_FEATURE_ACCESS_DENIED("error.subscription-feature-access-denied"),
    EMAIL_NOT_VERIFIED("error.email.not-verified"),
    PRODUCT_NOT_FOUND("error.product.not-found"),
    EXERCISE_LOG_NOT_FOUND("error.exercise-log.not-found"),
    PROGRESS_LOG_NOT_FOUND("error.progress-log.not-found"),
    EXERCISE_ITEM_NOT_FOUND("error.exercise-item.not-found"),
    DUPLICATE_EXTERNAL_EXERCISE_LOG("error.duplicate.external-exercise-log"),
    DUPLICATE_EXERCISE_ITEM("error.duplicate.exercise-item"),
    DUPLICATE_RECIPE_PUBLICATION_REQUEST("error.duplicate.recipe-publication-request"),
    DUPLICATE_MANUAL_STEP_LOG("error.duplicate.manual-step-log"),
    RESOURCE_NOT_FOUND("error.resource.not-found"),
    METHOD_NOT_ALLOWED("error.method-not-allowed"),
    AI_PROVIDER_ERROR("error.ai-provider"),
    AI_TIMEOUT("error.ai-timeout"),
    INVALID_REQUEST("error.invalid.request"),
    VALIDATION_ERROR("error.validation"),
    UPLOAD_TOO_LARGE("error.upload.too-large"),
    REQUEST_CONFLICT("error.request-conflict"),
    DATA_INTEGRITY_VIOLATION("error.data-integrity"),
    CONCURRENT_UPDATE("error.concurrent-update"),
    UNEXPECTED_ERROR("error.unexpected"),
    RATE_LIMIT_EXCEEDED("error.rate-limit-exceeded"),
    EMAIL_ALREADY_REGISTERED("error.email-already-registered");

    private final String messageKey;

    ApiErrorCode(String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }

    public static ApiErrorCode fromMessageKey(String messageKey) {
        return Arrays.stream(values())
                .filter(value -> value.messageKey.equals(messageKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown API error message key: " + messageKey));
    }
}
