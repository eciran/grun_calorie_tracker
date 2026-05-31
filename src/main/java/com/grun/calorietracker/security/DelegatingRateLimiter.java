package com.grun.calorietracker.security;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@RequiredArgsConstructor
public class DelegatingRateLimiter implements RequestRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(DelegatingRateLimiter.class);

    private final InMemoryRateLimiter inMemoryRateLimiter;
    private final ObjectProvider<RedisRateLimiter> redisRateLimiterProvider;

    @Override
    public boolean isAllowed(String key, int maxRequests, long windowMillis) {
        RedisRateLimiter redisRateLimiter = redisRateLimiterProvider.getIfAvailable();
        if (redisRateLimiter == null) {
            return inMemoryRateLimiter.isAllowed(key, maxRequests, windowMillis);
        }
        try {
            return redisRateLimiter.isAllowed(key, maxRequests, windowMillis);
        } catch (RuntimeException ex) {
            log.warn("Redis rate limiter failed. Falling back to in-memory limiter for key={}", key, ex);
            return inMemoryRateLimiter.isAllowed(key, maxRequests, windowMillis);
        }
    }
}
