package com.grun.calorietracker.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryRateLimiter implements RequestRateLimiter {

    private final Clock clock;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public InMemoryRateLimiter() {
        this(Clock.systemUTC());
    }

    InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean isAllowed(String key, int maxRequests, long windowMillis) {
        long now = clock.millis();
        WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter(now));
        synchronized (counter) {
            if (now - counter.windowStartMillis >= windowMillis) {
                counter.windowStartMillis = now;
                counter.requestCount = 0;
            }
            if (counter.requestCount >= maxRequests) {
                return false;
            }
            counter.requestCount++;
            return true;
        }
    }

    private static class WindowCounter {
        private long windowStartMillis;
        private int requestCount;

        private WindowCounter(long windowStartMillis) {
            this.windowStartMillis = windowStartMillis;
        }
    }
}
