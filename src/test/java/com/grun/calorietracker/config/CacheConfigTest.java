package com.grun.calorietracker.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigTest {

    private final CacheConfig cacheConfig = new CacheConfig(new SimpleMeterRegistry());

    @Test
    void redisSerializerRoundTripsJavaTimeValues() {
        var configuration = cacheConfig.redisCacheConfiguration(Duration.ofMinutes(1), "grun:test:");
        CachePayload payload = new CachePayload(
                LocalDate.of(2026, 7, 28),
                LocalDateTime.of(2026, 7, 28, 12, 30)
        );

        var serialized = configuration.getValueSerializationPair().write(payload);
        Object restored = configuration.getValueSerializationPair().read(serialized);

        assertThat(restored).isEqualTo(payload);
    }

    private record CachePayload(LocalDate date, LocalDateTime timestamp) {
    }
}