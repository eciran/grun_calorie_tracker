package com.grun.calorietracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    public RedisCacheConfiguration redisCacheConfiguration(
            @Value("${spring.cache.redis.time-to-live:10m}") Duration timeToLive,
            @Value("${spring.cache.redis.key-prefix:grun:prod:}") String keyPrefix
    ) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(timeToLive)
                .disableCachingNullValues()
                .prefixCacheNameWith(keyPrefix)
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer()
                ));
    }
}