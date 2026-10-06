package com.grun.calorietracker.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

@EnabledIfSystemProperty(named = "grun.security.redis", matches = "true")
class RedisRateLimiterConcurrencyTest {
    @Test void twoLimiterInstancesShareAtomicBudgetAndAlwaysExpireTheirKeys() throws Exception {
        var factory = new LettuceConnectionFactory("localhost", 55434);
        factory.afterPropertiesSet();
        factory.start();
        var template = new StringRedisTemplate(factory);
        String key = "synthetic-security-" + UUID.randomUUID();
        var executor = Executors.newFixedThreadPool(8);
        var ready = new CountDownLatch(8);
        var start = new CountDownLatch(1);
        var first = new RedisRateLimiter(template);
        var second = new RedisRateLimiter(template);
        long window = 3_600_000;
        try {
            var futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 8; i++) {
                var limiter = i % 2 == 0 ? first : second;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                    int accepted = 0;
                    for (int j = 0; j < 10; j++) if (limiter.isAllowed(key, 15, window)) accepted++;
                    return accepted;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int accepted = 0;
            for (var future : futures) accepted += future.get(20, TimeUnit.SECONDS);
            assertThat(accepted).isEqualTo(15);
            var keys = template.keys("grun:ratelimit:" + key + ":*");
            assertThat(keys).hasSize(1);
            String redisKey = keys.iterator().next();
            assertThat(template.getExpire(redisKey, TimeUnit.MILLISECONDS)).isPositive();
            template.persist(redisKey);
            assertThat(template.getExpire(redisKey, TimeUnit.MILLISECONDS)).isEqualTo(-1);
            assertThat(first.isAllowed(key, 15, window)).isFalse();
            assertThat(template.getExpire(redisKey, TimeUnit.MILLISECONDS)).isPositive();
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            var keys = template.keys("grun:ratelimit:" + key + ":*");
            if (keys != null && !keys.isEmpty()) template.delete(keys);
            factory.destroy();
        }
    }
}
