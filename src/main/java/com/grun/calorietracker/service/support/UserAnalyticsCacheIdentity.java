package com.grun.calorietracker.service.support;

public record UserAnalyticsCacheIdentity(
        Long userId,
        long revision,
        String timeZone
) {
}

