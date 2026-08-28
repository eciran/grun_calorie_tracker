package com.grun.calorietracker.exception;

/** Stable goal validation code; diagnostic details are not rendered to clients. */
public class GoalValidationException extends IllegalArgumentException {
    private final String code;
    public GoalValidationException(String code, String diagnosticMessage) {
        super(diagnosticMessage);
        this.code = code;
    }
    public String getCode() { return code; }
}
