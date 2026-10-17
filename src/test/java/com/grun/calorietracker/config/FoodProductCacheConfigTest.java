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
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FoodProductCacheConfigTest {
    AtomicLong epoch = new AtomicLong(1);
    ApplicationContextRunner context() {
        var revision = mock(FoodProductCacheRevision.class);
        when(revision.current()).thenAnswer(call -> epoch.get());
        when(revision.key(any())).thenAnswer(call -> new FoodProductCacheRevision.Key(epoch.get(), call.getArgument(0)));
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class))
                .withUserConfiguration(CacheConfig.class, FoodProductCacheConfig.class, QueriesConfig.class)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
                .withBean("foodProductCacheRevision", FoodProductCacheRevision.class, () -> revision)
                .withPropertyValues("spring.cache.type=simple");
    }

    @Test void scopedResolverPreservesGlobalManagerAndFreezesSnapshots() {
        context().run(app -> {
            assertThat(app).hasNotFailed();
            assertThat(app.getBean(CacheManager.class)).isInstanceOf(ConcurrentMapCacheManager.class);
            var queries = app.getBean(Queries.class);
            var first = queries.product("milk"); first.getContent().get(0).setProductName("tampered");
            assertThat(queries.product("milk").getContent().get(0).getProductName()).isEqualTo("milk");
            assertThat(queries.getCalls()).isEqualTo(1);
            epoch.incrementAndGet(); queries.product("milk"); assertThat(queries.getCalls()).isEqualTo(2);
            queries.unrelated("other"); queries.unrelated("other"); assertThat(queries.getOtherCalls()).isEqualTo(1);
        });
    }

    @Test void disabledProductCacheLeavesUnrelatedCacheWorking() {
        context().withPropertyValues("grun.product-cache.enabled=false").run(app -> {
            var queries = app.getBean(Queries.class);
            queries.product("milk"); queries.product("milk"); assertThat(queries.getCalls()).isEqualTo(2);
            queries.unrelated("other"); queries.unrelated("other"); assertThat(queries.getOtherCalls()).isEqualTo(1);
        });
    }

    @Test void rejectsZeroTtl() {
        context().withPropertyValues("grun.product-cache.search.ttl=0s").run(app -> assertThat(app).hasFailed());
    }

    @Test void globalNoneDisablesProductAndUnrelatedCaches() {
        context().withPropertyValues("spring.cache.type=none").run(app -> {
            assertThat(app).hasNotFailed();
            var queries = app.getBean(Queries.class);
            queries.product("milk"); queries.product("milk");
            queries.unrelated("other"); queries.unrelated("other");
            assertThat(queries.getCalls()).isEqualTo(2);
            assertThat(queries.getOtherCalls()).isEqualTo(2);
        });
    }

    @Test void rejectsUnsafePayloadBudget() {
        context().withPropertyValues("grun.product-cache.search.max-entries=10000").run(app -> assertThat(app).hasFailed());
    }

    @Test void rejectsUnboundedWaitOrNegativeTtl() {
        context().withPropertyValues("grun.product-cache.coalescing.wait-timeout=6s").run(app -> assertThat(app).hasFailed());
        context().withPropertyValues("grun.product-cache.negative-ttl=31s").run(app -> assertThat(app).hasFailed());
        context().withPropertyValues("grun.product-cache.coalescing.max-concurrent-loads=0").run(app -> assertThat(app).hasFailed());
    }

    @Configuration(proxyBeanMethods = false) static class QueriesConfig {
        @Bean Queries queries() { return new Queries(); }
    }
    public static class Queries {
        public int calls;
        public int otherCalls;
        public int getCalls() { return calls; }
        public int getOtherCalls() { return otherCalls; }
        @Cacheable(cacheNames = "foodProductSearch", cacheResolver = "foodProductCacheResolver",
                key = "@foodProductCacheRevision.key(#name)")
        public FoodProductSearchPageDto product(String name) {
            calls++; var product = new FoodProductDto(); product.setProductName(name);
            var page = new FoodProductSearchPageDto(); page.setContent(List.of(product)); return page;
        }
        @Cacheable(cacheNames = "otherCache")
        public String unrelated(String name) { otherCalls++; return name; }
    }
}
