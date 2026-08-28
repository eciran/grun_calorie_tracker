package com.grun.calorietracker.exception;

/** Insufficient credits for new AI work; this does not revoke plan access. */
public class AiQuotaExhaustedException extends IllegalArgumentException {
    public AiQuotaExhaustedException() {
        super("AI quota is not available for the requested operation.");
    }
}
