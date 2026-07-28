package com.grun.calorietracker.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig implements CachingConfigurer {

    private final MeterRegistry meterRegistry;

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    public RedisCacheConfiguration redisCacheConfiguration(
            @Value("${spring.cache.redis.time-to-live:10m}") Duration timeToLive,
            @Value("${spring.cache.redis.key-prefix:grun:local:}") String keyPrefix
    ) {
        GenericJackson2JsonRedisSerializer valueSerializer = GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(new ObjectMapper().findAndRegisterModules())
                .defaultTyping(true)
                .build();

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(timeToLive)
                .disableCachingNullValues()
                .prefixCacheNameWith(keyPrefix)
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        valueSerializer
                ));
    }

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    public RedisCacheManagerBuilderCustomizer userAnalyticsCacheTtlCustomizer(
            RedisCacheConfiguration defaultConfiguration,
            @Value("${grun.cache.analytics.dashboard-ttl:60s}") Duration dashboardTtl,
            @Value("${grun.cache.analytics.progress-ttl:2m}") Duration progressTtl,
            @Value("${grun.cache.analytics.energy-balance-ttl:2m}") Duration energyBalanceTtl,
            @Value("${grun.cache.analytics.micronutrients-ttl:5m}") Duration micronutrientsTtl,
            @Value("${grun.cache.analytics.tracking-daily-ttl:60s}") Duration trackingDailyTtl,
            @Value("${grun.cache.analytics.tracking-range-ttl:2m}") Duration trackingRangeTtl
    ) {
        return builder -> builder
                .withCacheConfiguration(UserAnalyticsCacheNames.DASHBOARD_DAILY_SUMMARY,
                        defaultConfiguration.entryTtl(dashboardTtl))
                .withCacheConfiguration(UserAnalyticsCacheNames.PROGRESS_BASIC,
                        defaultConfiguration.entryTtl(progressTtl))
                .withCacheConfiguration(UserAnalyticsCacheNames.PROGRESS_ADVANCED,
                        defaultConfiguration.entryTtl(progressTtl))
                .withCacheConfiguration(UserAnalyticsCacheNames.ENERGY_BALANCE,
                        defaultConfiguration.entryTtl(energyBalanceTtl))
                .withCacheConfiguration(UserAnalyticsCacheNames.MICRONUTRIENTS,
                        defaultConfiguration.entryTtl(micronutrientsTtl))
                .withCacheConfiguration(UserAnalyticsCacheNames.WATER_DAILY, defaultConfiguration.entryTtl(trackingDailyTtl))
                .withCacheConfiguration(UserAnalyticsCacheNames.WATER_RANGE, defaultConfiguration.entryTtl(trackingRangeTtl))
                .withCacheConfiguration(UserAnalyticsCacheNames.STEP_DAILY, defaultConfiguration.entryTtl(trackingDailyTtl))
                .withCacheConfiguration(UserAnalyticsCacheNames.STEP_RANGE, defaultConfiguration.entryTtl(trackingRangeTtl))
                .withCacheConfiguration(UserAnalyticsCacheNames.STEP_MANUAL_LOGS, defaultConfiguration.entryTtl(trackingDailyTtl))
                .withCacheConfiguration(UserAnalyticsCacheNames.FASTING_DAILY, defaultConfiguration.entryTtl(trackingDailyTtl))
                .withCacheConfiguration(UserAnalyticsCacheNames.FASTING_RANGE, defaultConfiguration.entryTtl(trackingRangeTtl))
                .withCacheConfiguration(UserAnalyticsCacheNames.FASTING_HISTORY, defaultConfiguration.entryTtl(trackingRangeTtl))
                .withCacheConfiguration(UserAnalyticsCacheNames.SLEEP_DAILY, defaultConfiguration.entryTtl(trackingDailyTtl))
                .withCacheConfiguration(UserAnalyticsCacheNames.SLEEP_WEEKLY, defaultConfiguration.entryTtl(trackingRangeTtl))
                .withCacheConfiguration(UserAnalyticsCacheNames.SLEEP_SESSIONS, defaultConfiguration.entryTtl(trackingRangeTtl))
                .withCacheConfiguration(UserAnalyticsCacheNames.HEALTH_DAILY, defaultConfiguration.entryTtl(trackingDailyTtl))
                .withCacheConfiguration(UserAnalyticsCacheNames.HEALTH_RANGE, defaultConfiguration.entryTtl(trackingRangeTtl));
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new FailOpenCacheErrorHandler(meterRegistry);
    }
}
