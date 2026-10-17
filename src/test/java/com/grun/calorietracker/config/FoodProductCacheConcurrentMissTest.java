package com.grun.calorietracker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.service.support.FoodProductCacheRevision;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FoodProductCacheConcurrentMissTest {
    @Test void eightConcurrentColdCallsComputeOnceAndReceiveIndependentSnapshots() {
        var revision = mock(FoodProductCacheRevision.class);
        when(revision.current()).thenReturn(1L);
        when(revision.key(any())).thenAnswer(call -> new FoodProductCacheRevision.Key(1L, call.getArgument(0)));
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class))
                .withUserConfiguration(CacheConfig.class, FoodProductCacheConfig.class, QueriesConfiguration.class)
                .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withBean("foodProductCacheRevision", FoodProductCacheRevision.class, () -> revision)
                .withPropertyValues("spring.cache.type=simple").run(app -> {
                    var query = app.getBean(Queries.class);
                    var meters = app.getBean(MeterRegistry.class);
                    var executor = Executors.newFixedThreadPool(8);
                    var start = new CyclicBarrier(8);
                    var results = new java.util.ArrayList<Future<FoodProductSearchPageDto>>();
                    try {
                        for (int i = 0; i < 8; i++) results.add(executor.submit(() -> {
                            start.await(3, TimeUnit.SECONDS); return query.search("milk");
                        }));
                        assertTrue(query.entered().await(3, TimeUnit.SECONDS));
                        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(750);
                        while (System.nanoTime() < deadline && query.loads() < 8 && waits(meters) < 7)
                            Thread.sleep(5);
                        query.release().countDown();
                        var pages = new java.util.ArrayList<FoodProductSearchPageDto>();
                        for (var result : results) pages.add(result.get(4, TimeUnit.SECONDS));
                        assertEquals(1, query.loads(), "Cold same-key requests must coalesce outside their DB transactions");
                        pages.get(0).getContent().get(0).setProductName("caller mutation");
                        for (int i = 1; i < pages.size(); i++) assertEquals("milk", pages.get(i).getContent().get(0).getProductName());
                    } finally {
                        query.release().countDown(); executor.shutdownNow();
                        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
                    }
                });
    }

    static double waits(MeterRegistry meters) {
        var counter = meters.find("grun.product.cache.coalescing").tag("result", "wait").counter();
        return counter == null ? 0 : counter.count();
    }
    @Configuration(proxyBeanMethods = false) static class QueriesConfiguration {
        @Bean Queries queries() { return new Queries(); }
    }
    public static class Queries {
        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        public int loads() { return calls.get(); }
        public CountDownLatch entered() { return entered; }
        public CountDownLatch release() { return release; }
        @Cacheable(cacheNames = "foodProductSearch", cacheResolver = "foodProductCacheResolver",
                key = "@foodProductCacheRevision.key(#query)", unless = "#result == null")
        public FoodProductSearchPageDto search(String query) throws InterruptedException {
            calls.incrementAndGet(); entered.countDown();
            if (!release.await(4, TimeUnit.SECONDS)) throw new IllegalStateException("Test loader release timed out");
            var product = new FoodProductDto(); product.setProductName(query);
            var page = new FoodProductSearchPageDto(); page.setContent(List.of(product)); page.setTotalElements(1L);
            return page;
        }
    }
}
