package com.grun.calorietracker.enums;

import org.springframework.http.HttpStatus;

public enum FastingSafetyErrorCode {
    FASTING_ADVANCED_NOT_ELIGIBLE(HttpStatus.UNPROCESSABLE_ENTITY),
    FASTING_UNSAFE_DURATION(HttpStatus.UNPROCESSABLE_ENTITY),
    FASTING_SAFETY_ACKNOWLEDGEMENT_REQUIRED(HttpStatus.PRECONDITION_REQUIRED);

    private final HttpStatus status;

    FastingSafetyErrorCode(HttpStatus status) { this.status = status; }

    public HttpStatus status() { return status; }
}
