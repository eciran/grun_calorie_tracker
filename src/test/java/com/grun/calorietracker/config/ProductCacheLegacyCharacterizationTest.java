package com.grun.calorietracker.config;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.service.impl.FoodItemServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import static org.junit.jupiter.api.Assertions.*;

class ProductCacheLegacyCharacterizationTest {
    @Test
    void stockSimpleCacheHasNoEntryBound() {
        var legacy = new ConcurrentMapCache("legacy");
        for (int i = 0; i < 100; i++) legacy.put(i, new FoodProductDto());
        assertEquals(100, legacy.getNativeCache().size());
    }

    @Test
    void stockSimpleCacheSharesMutableProductObjects() {
        var legacy = new ConcurrentMapCache("legacy");
        var product = new FoodProductDto();
        product.setProductName("Original");
        legacy.put(1L, product);
        legacy.get(1L, FoodProductDto.class).setProductName("Changed by caller");
        assertEquals("Changed by caller", legacy.get(1L, FoodProductDto.class).getProductName());
    }

    @Test
    void productReadsMustUseIsolatedRevisionAwareCache() {
        for (var method : FoodItemServiceImpl.class.getDeclaredMethods()) {
            var annotation = method.getAnnotation(Cacheable.class);
            if (annotation == null) continue;
            assertEquals("foodProductCacheResolver", annotation.cacheResolver(), method.getName());
            assertTrue(annotation.key().contains("foodProductCacheRevision"));
        }
    }
}
