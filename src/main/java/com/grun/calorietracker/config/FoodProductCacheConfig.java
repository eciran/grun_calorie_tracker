package com.grun.calorietracker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Ticker;
import com.grun.calorietracker.service.support.FoodProductCacheRevision;
import com.grun.calorietracker.service.support.FoodProductSnapshotCache;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.BeanFactory;

import java.util.Map;

@Configuration
@EnableConfigurationProperties(FoodProductCacheProperties.class)
public class FoodProductCacheConfig {
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public FoodProductCacheReadAdvisor foodProductCacheReadAdvisor(BeanFactory beans,
            FoodProductCacheProperties properties, MeterRegistry meters,
            @Value("${spring.cache.type:simple}") String globalType) {
        return new FoodProductCacheReadAdvisor(beans, properties, meters,
                properties.isEnabled() && !globalType.equalsIgnoreCase("none"));
    }
    @Bean("foodProductCacheResolver")
    public CacheResolver foodProductCacheResolver(FoodProductCacheProperties properties,
            FoodProductCacheRevision revision, ObjectMapper mapper, MeterRegistry meters,
            @Value("${spring.cache.type:simple}") String globalType) {
        boolean enabled = properties.isEnabled() && !globalType.equalsIgnoreCase("none");
        Map<String, Cache> caches = Map.of(
                "foodProductSearch", new FoodProductSnapshotCache("foodProductSearch", properties.getSearch(), revision,
                        mapper, meters, enabled, Ticker.systemTicker(), properties.getNegativeTtl()),
                "foodProductById", new FoodProductSnapshotCache("foodProductById", properties.getDetail(), revision,
                        mapper, meters, enabled, Ticker.systemTicker(), properties.getNegativeTtl()),
                "foodProductByBarcode", new FoodProductSnapshotCache("foodProductByBarcode", properties.getDetail(), revision,
                        mapper, meters, enabled, Ticker.systemTicker(), properties.getNegativeTtl()));
        // A scoped resolver does not suppress Boot's default cache manager for other modules.
        return context -> context.getOperation().getCacheNames().stream().map(name -> {
            Cache cache = caches.get(name);
            if (cache == null) throw new IllegalArgumentException("Not a product cache: " + name);
            return cache;
        }).toList();
    }
}
