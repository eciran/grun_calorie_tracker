package com.grun.calorietracker.exception;

public class AdminAuthenticationRateLimitException extends RuntimeException {
    private final int retryAfterSeconds;

    public AdminAuthenticationRateLimitException(int retryAfterSeconds) {
        super("Too many authentication attempts. Please wait before trying again.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
