package com.grun.calorietracker.integration;

import com.grun.calorietracker.config.UserAnalyticsCacheNames;
import com.grun.calorietracker.enums.AnalyticsMutationSource;
import com.grun.calorietracker.service.UserAnalyticsCacheRevisionService;
import com.grun.calorietracker.service.support.UserAnalyticsCacheGateway;
import com.grun.calorietracker.service.support.UserAnalyticsCacheIdentity;
import com.grun.calorietracker.service.support.UserAnalyticsCacheKeyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "GRUN_RUN_REDIS_INTEGRATION_TESTS", matches = "true")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.profiles.active=local",
                "spring.datasource.driver-class-name=org.postgresql.Driver",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
                "spring.flyway.enabled=true",
                "spring.cache.type=redis",
                "spring.cache.redis.key-prefix=grun:integration:",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379",
                "spring.data.redis.connect-timeout=1s",
                "spring.data.redis.timeout=1s",
                "grun.cache.analytics.tracking-daily-ttl=250ms",
                "grun.local.admin.bootstrap-enabled=true",
                "grun.local.admin.email=cache-integration@grun.local",
                "grun.local.admin.password=CacheIntegrationPass1!",
                "grun.rate-limit.enabled=false",
                "grun.rate-limit.redis.enabled=false"
        }
)
class UserAnalyticsCachePostgresRedisIntegrationTest {

    private static final String TEST_EMAIL = "cache-integration@grun.local";
    private static final String KEY_PREFIX = "grun:integration:";

    @Autowired
    private UserAnalyticsCacheGateway cacheGateway;

    @Autowired
    private UserAnalyticsCacheRevisionService revisionService;

    @Autowired
    private UserAnalyticsCacheKeyFactory cacheKeyFactory;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearIntegrationKeys() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void cacheHitAndTtlExpiryUseRealRedis() throws InterruptedException {
        AtomicInteger loads = new AtomicInteger();

        String first = cacheGateway.get(
                UserAnalyticsCacheNames.WATER_DAILY,
                "101:0:daily:2026-07-28:Europe_Dublin",
                () -> "value-" + loads.incrementAndGet()
        );
        String cached = cacheGateway.get(
                UserAnalyticsCacheNames.WATER_DAILY,
                "101:0:daily:2026-07-28:Europe_Dublin",
                () -> "value-" + loads.incrementAndGet()
        );

        assertThat(first).isEqualTo("value-1");
        assertThat(cached).isEqualTo(first);
        assertThat(loads).hasValue(1);
        assertThat(redisTemplate.keys(KEY_PREFIX + "*waterDailySummary*")).hasSize(1);

        Thread.sleep(450);

        String refreshed = cacheGateway.get(
                UserAnalyticsCacheNames.WATER_DAILY,
                "101:0:daily:2026-07-28:Europe_Dublin",
                () -> "value-" + loads.incrementAndGet()
        );

        assertThat(refreshed).isEqualTo("value-2");
        assertThat(loads).hasValue(2);
    }

    @Test
    void userScopedKeysDoNotOverwriteEachOther() {
        String firstUser = cacheGateway.get(
                UserAnalyticsCacheNames.STEP_DAILY,
                "201:0:daily:2026-07-28:Europe_Dublin",
                () -> "first-user"
        );
        String secondUser = cacheGateway.get(
                UserAnalyticsCacheNames.STEP_DAILY,
                "202:0:daily:2026-07-28:Europe_Dublin",
                () -> "second-user"
        );

        assertThat(firstUser).isEqualTo("first-user");
        assertThat(secondUser).isEqualTo("second-user");
        assertThat(redisTemplate.keys(KEY_PREFIX + "*stepDailySummary*")).hasSize(2);
    }

    @Test
    void rolledBackMutationDoesNotAdvanceRevision() {
        long before = revisionService.requireIdentity(TEST_EMAIL).revision();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            revisionService.bumpForEmail(TEST_EMAIL, AnalyticsMutationSource.WATER);
            status.setRollbackOnly();
        });

        long after = revisionService.requireIdentity(TEST_EMAIL).revision();
        assertThat(after).isEqualTo(before);
    }

    @Test
    void configuredCacheManagerUsesRedis() {
        assertThat(cacheManager.getClass().getName()).contains("RedisCacheManager");
    }

    @Test
    void concurrentRequestsForSameKeyComputeOnce() throws Exception {
        int requestCount = 8;
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(requestCount);
        try {
            List<Future<String>> futures = IntStream.range(0, requestCount)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return cacheGateway.get(UserAnalyticsCacheNames.STEP_DAILY, "concurrent-key", () -> {
                            loads.incrementAndGet();
                            try {
                                Thread.sleep(150);
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(interrupted);
                            }
                            return "shared-value";
                        });
                    })).toList();
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<String> future : futures) {
                assertThat(future.get(2, TimeUnit.SECONDS)).isEqualTo("shared-value");
            }
            assertThat(loads).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void committedRevisionMakesOldKeyUnreachableWithoutAffectingAnotherUser() {
        UserAnalyticsCacheIdentity before = revisionService.requireIdentity(TEST_EMAIL);
        LocalDate date = LocalDate.of(2026, 7, 28);
        String oldKey = cacheKeyFactory.key(before, "daily", date);
        String otherUserKey = cacheKeyFactory.key(
                new UserAnalyticsCacheIdentity(before.userId() + 10_000, before.revision(), before.timeZone()), "daily", date);
        cacheGateway.get(UserAnalyticsCacheNames.WATER_DAILY, oldKey, () -> "old-value");
        cacheGateway.get(UserAnalyticsCacheNames.WATER_DAILY, otherUserKey, () -> "other-value");

        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> revisionService.bumpForEmail(TEST_EMAIL, AnalyticsMutationSource.WATER));

        UserAnalyticsCacheIdentity after = revisionService.requireIdentity(TEST_EMAIL);
        String newKey = cacheKeyFactory.key(after, "daily", date);
        AtomicInteger refreshedLoads = new AtomicInteger();
        assertThat(after.revision()).isEqualTo(before.revision() + 1);
        assertThat(newKey).isNotEqualTo(oldKey);
        assertThat(cacheGateway.get(UserAnalyticsCacheNames.WATER_DAILY, newKey, () -> {
            refreshedLoads.incrementAndGet();
            return "fresh-value";
        })).isEqualTo("fresh-value");
        assertThat(cacheGateway.get(UserAnalyticsCacheNames.WATER_DAILY, otherUserKey, () -> "unexpected"))
                .isEqualTo("other-value");
        assertThat(refreshedLoads).hasValue(1);
    }

    @Test
    void generatedRedisKeysDoNotContainEmailOrOtherPii() {
        UserAnalyticsCacheIdentity identity = revisionService.requireIdentity(TEST_EMAIL);
        String key = cacheKeyFactory.key(identity, "daily", LocalDate.of(2026, 7, 28), "Europe/Dublin");
        cacheGateway.get(UserAnalyticsCacheNames.WATER_DAILY, key, () -> "value");

        Set<String> redisKeys = redisTemplate.keys(KEY_PREFIX + "*");
        assertThat(redisKeys).isNotNull().isNotEmpty();
        assertThat(redisKeys).noneMatch(redisKey ->
                redisKey.contains(TEST_EMAIL) || redisKey.contains("@") || redisKey.contains("cache-integration"));
    }

    @Test
    void javaTimePayloadRoundTripsThroughRedis() {
        AtomicInteger loads = new AtomicInteger();
        IntegrationValue expected = new IntegrationValue(LocalDate.of(2026, 7, 28), 8_250);
        IntegrationValue first = cacheGateway.get(UserAnalyticsCacheNames.STEP_DAILY, "java-time-round-trip", () -> {
            loads.incrementAndGet();
            return expected;
        });
        IntegrationValue cached = cacheGateway.get(UserAnalyticsCacheNames.STEP_DAILY, "java-time-round-trip", () -> {
            loads.incrementAndGet();
            return new IntegrationValue(LocalDate.MIN, 0);
        });
        assertThat(first).isEqualTo(expected);
        assertThat(cached).isEqualTo(expected);
        assertThat(loads).hasValue(1);
    }

    private record IntegrationValue(LocalDate date, int total) {
    }}
