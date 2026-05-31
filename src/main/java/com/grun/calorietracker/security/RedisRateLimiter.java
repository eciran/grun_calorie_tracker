package com.grun.calorietracker.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "grun.rate-limit.redis", name = "enabled", havingValue = "true")
public class RedisRateLimiter implements RequestRateLimiter {

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean isAllowed(String key, int maxRequests, long windowMillis) {
        long now = System.currentTimeMillis();
        long bucket = now / windowMillis;
        String redisKey = "grun:ratelimit:" + key + ":" + bucket;

        Long current = redisTemplate.opsForValue().increment(redisKey);
        if (current == null) {
            return false;
        }

        if (current == 1L) {
            redisTemplate.expire(redisKey, Duration.ofMillis(windowMillis + 5_000));
        }
        return current <= maxRequests;
    }
}

