package com.grun.calorietracker.enums;

import org.springframework.http.HttpStatus;

public enum AdvancedFastingErrorCode {
    ACTIVE_OCCURRENCE_CANNOT_BE_SKIPPED(HttpStatus.CONFLICT),
    FAST_OCCURRENCE_REQUIRED(HttpStatus.BAD_REQUEST),
    SKIPPED_OCCURRENCE_CANNOT_BE_STARTED(HttpStatus.CONFLICT),
    ACTIVE_FASTING_SESSION_EXISTS(HttpStatus.CONFLICT),
    ARCHIVED_FASTING_PROGRAM(HttpStatus.CONFLICT),
    INVALID_FASTING_PROGRAM_DATES(HttpStatus.BAD_REQUEST),
    INVALID_WEEKLY_FASTING_RULES(HttpStatus.BAD_REQUEST),
    INVALID_FASTING_DAY_RULE(HttpStatus.BAD_REQUEST),
    INVALID_FIVE_TWO_SCHEDULE(HttpStatus.BAD_REQUEST),
    INVALID_FASTING_ANALYTICS_RANGE(HttpStatus.BAD_REQUEST),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT),
    INVALID_FASTING_PROGRAM_TRANSITION(HttpStatus.CONFLICT),
    INVALID_FASTING_PROGRAM_STATUS_FILTER(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    AdvancedFastingErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}