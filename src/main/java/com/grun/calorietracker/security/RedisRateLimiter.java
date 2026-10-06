package com.grun.calorietracker.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "grun.rate-limit.redis", name = "enabled", havingValue = "true")
public class RedisRateLimiter implements RequestRateLimiter {
    private static final DefaultRedisScript<Long> INCREMENT_WITH_EXPIRY = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if redis.call('PTTL', KEYS[1]) < 0 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean isAllowed(String key, int maxRequests, long windowMillis) {
        if (windowMillis <= 0) throw new IllegalArgumentException("Rate limit window must be positive");
        long now = System.currentTimeMillis();
        long bucket = now / windowMillis;
        String redisKey = "grun:ratelimit:" + key + ":" + bucket;

        Long current = redisTemplate.execute(INCREMENT_WITH_EXPIRY, List.of(redisKey),
                Long.toString(windowMillis + 5_000));
        if (current == null) {
            return false;
        }

        return current <= maxRequests;
    }
}

