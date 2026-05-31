package com.grun.calorietracker.security;

public interface RequestRateLimiter {
    boolean isAllowed(String key, int maxRequests, long windowMillis);
}

