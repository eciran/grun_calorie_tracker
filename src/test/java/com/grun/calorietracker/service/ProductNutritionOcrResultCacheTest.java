package com.grun.calorietracker.service;

import com.grun.calorietracker.config.ProductNutritionOcrProperties;
import com.grun.calorietracker.service.impl.InMemoryProductNutritionOcrResultCache;
import com.grun.calorietracker.service.model.ProductNutritionOcrEvidence;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackRequest;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductNutritionOcrResultCacheTest {
    @Test
    void isolatesUsersCasesParserAndModelAndExpiresByTtl() {
        ProductNutritionOcrProperties properties = new ProductNutritionOcrProperties();
        properties.setCacheTtl(Duration.ofMinutes(5));
        MutableClock clock = new MutableClock();
        ProductNutritionOcrResultCache cache = new InMemoryProductNutritionOcrResultCache(properties, clock);
        ProductNutritionOcrEvidence evidence = new ProductNutritionOcrEvidence(new byte[]{1}, "image/png", "sha");
        ProductNutritionOcrFallbackRequest original = request(30L, 10L, "v4");
        ProductNutritionOcrFallbackResult result = new ProductNutritionOcrFallbackResult("GEMINI", "m1", Map.of());
        cache.put(original, evidence, "m1", result);

        assertEquals(result, cache.get(original, evidence, "m1").orElseThrow());
        assertTrue(cache.get(request(31L, 10L, "v4"), evidence, "m1").isEmpty());
        assertTrue(cache.get(request(30L, 11L, "v4"), evidence, "m1").isEmpty());
        assertTrue(cache.get(request(30L, 10L, "v5"), evidence, "m1").isEmpty());
        assertTrue(cache.get(original, evidence, "m2").isEmpty());
        clock.advance(Duration.ofMinutes(5));
        assertTrue(cache.get(original, evidence, "m1").isEmpty());
    }

    private ProductNutritionOcrFallbackRequest request(Long userId, Long caseId, String parser) {
        return new ProductNutritionOcrFallbackRequest(caseId, 20L, userId, parser, 0.5,
                List.of("energy"), List.of(), true, "ai-v1");
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-08-27T10:00:00Z");
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
