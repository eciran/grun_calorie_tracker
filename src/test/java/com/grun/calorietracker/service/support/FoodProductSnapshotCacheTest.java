package com.grun.calorietracker.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.FoodProductCacheProperties;
import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.dto.FoodServingOptionDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoodProductSnapshotCacheTest {
    AtomicLong clock = new AtomicLong();
    AtomicLong generation = new AtomicLong(1);
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    FoodProductCacheRevision revision;
    FoodProductSnapshotCache cache;

    @BeforeEach void setup() {
        revision = mock(FoodProductCacheRevision.class);
        when(revision.current()).thenAnswer(call -> generation.get());
        cache = makeCache(32, 65_536);
    }

    FoodProductSnapshotCache makeCache(int entries, int bytes) {
        return new FoodProductSnapshotCache("foodProductSearch",
                new FoodProductCacheProperties.Policy(Duration.ofSeconds(30), entries, bytes), revision,
                new ObjectMapper().findAndRegisterModules(), meters, true, clock::get);
    }

    FoodProductCacheRevision.Key key(String dimension) { return new FoodProductCacheRevision.Key(generation.get(), dimension); }
    FoodProductSearchPageDto page(String name) {
        var product = new FoodProductDto(); product.setProductName(name);
        var option = new FoodServingOptionDto(); option.setLabel("1 cup"); option.setMlVolume(250.0);
        product.setServingOptions(List.of(option));
        var page = new FoodProductSearchPageDto(); page.setContent(List.of(product)); return page;
    }

    @Test void fixedTtlExpiresEvenAfterHits() {
        var key = key("ttl"); cache.put(key, page("milk"));
        clock.set(Duration.ofSeconds(29).toNanos()); assertNotNull(cache.get(key));
        clock.set(Duration.ofSeconds(31).toNanos()); assertNull(cache.get(key));
    }

    @Test void trueEmptySearchExpiresAfterFiveSecondsRatherThanPositiveTtl() {
        var empty = new FoodProductSearchPageDto(); empty.setContent(List.of()); empty.setTotalElements(0L);
        var key = key("missing"); cache.put(key, empty);
        clock.set(Duration.ofSeconds(4).toNanos()); assertNotNull(cache.get(key));
        clock.set(Duration.ofSeconds(6).toNanos()); assertNull(cache.get(key));
    }

    @Test void beyondLastPageIsNotANegativeSearchAndKeepsPositiveTtl() {
        var empty = new FoodProductSearchPageDto(); empty.setContent(List.of()); empty.setTotalElements(10L);
        var key = key("last-page"); cache.put(key, empty);
        clock.set(Duration.ofSeconds(6).toNanos()); assertNotNull(cache.get(key));
        clock.set(Duration.ofSeconds(31).toNanos()); assertNull(cache.get(key));
    }

    @Test void unavailableResponseNeverEntersStorageAndItsFlagIsNotPublicJson() throws Exception {
        var empty = new FoodProductSearchPageDto(); empty.setContent(List.of()); empty.setTotalElements(0L);
        empty.setCacheable(false); cache.put(key("unavailable"), empty); assertNull(cache.get(key("unavailable")));
        assertFalse(new ObjectMapper().writeValueAsString(empty).contains("cacheable"));
        assertFalse(empty.copyWithSearchRequestId("fresh-request").isCacheable());
    }

    @Test void zeroNegativeTtlDisablesNegativeStorageAndNeverExceedsPositiveTtl() {
        var empty = new FoodProductSearchPageDto(); empty.setContent(List.of()); empty.setTotalElements(0L);
        var off = new FoodProductSnapshotCache("foodProductSearch", new FoodProductCacheProperties().getSearch(),
                revision, new ObjectMapper(), meters, true, clock::get, Duration.ZERO);
        off.put(key("disabled-negative"), empty); assertNull(off.get(key("disabled-negative")));
        var shortTtl = new FoodProductSnapshotCache("foodProductSearch",
                new FoodProductCacheProperties.Policy(Duration.ofSeconds(2), 32, 65536),
                revision, new ObjectMapper(), meters, true, clock::get, Duration.ofSeconds(5));
        shortTtl.put(key("short-negative"), empty); clock.set(Duration.ofSeconds(3).toNanos());
        assertNull(shortTtl.get(key("short-negative")));
    }

    @Test void entryCountIsBounded() {
        cache = makeCache(2, 65_536);
        for (int i = 0; i < 100; i++) cache.put(key("entry-" + i), page("milk"));
        assertTrue(nativeCache().estimatedSize() <= 2);
    }

    @Test void oversizedResponsesAreNotCached() {
        cache = makeCache(2, 1024);
        cache.put(key("large"), page("milk".repeat(2000)));
        assertEquals(0, nativeCache().estimatedSize());
        assertEquals(1, meters.get("grun.product.cache.bypass").tag("reason", "oversize").counter().count());
    }

    @Test void callerAndNestedServingMutationsNeverChangeCachedSnapshot() {
        var key = key("snapshot"); var original = page("Original"); cache.put(key, original);
        original.getContent().get(0).setProductName("Source changed");
        var first = cache.get(key, FoodProductSearchPageDto.class);
        assertEquals("Original", first.getContent().get(0).getProductName());
        first.getContent().get(0).getServingOptions().get(0).setMlVolume(999.0);
        first.getContent().clear();
        assertEquals(250.0, cache.get(key, FoodProductSearchPageDto.class).getContent().get(0)
                .getServingOptions().get(0).getMlVolume());
    }

    @Test void oldInFlightLoaderCannotPublishAfterGenerationChanges() {
        var oldKey = key("race"); generation.incrementAndGet();
        cache.put(oldKey, page("old")); assertEquals(0, nativeCache().estimatedSize());
        cache.put(key("race"), page("new")); assertNotNull(cache.get(key("race")));
    }

    @Test void unavailableRevisionAndUnboundedKeysBypassStorage() {
        cache.put(new FoodProductCacheRevision.Key(null, "missing"), page("no"));
        cache.put(key("x".repeat(257)), page("no")); cache.put("legacy-key", page("no"));
        assertEquals(0, nativeCache().estimatedSize());
    }

    @Test void putsWaitForCommitAndCaptureBeforeCallerMutation() {
        begin(); var value = page("Before"); cache.put(key("tx"), value);
        value.getContent().get(0).setProductName("After"); assertNull(cache.get(key("tx")));
        finish(true);
        assertEquals("Before", cache.get(key("tx"), FoodProductSearchPageDto.class).getContent().get(0).getProductName());
    }

    @Test void rollbackNeverPublishesPendingPayload() {
        begin(); cache.put(key("rollback"), page("uncommitted")); finish(false);
        assertNull(cache.get(key("rollback")));
        assertFalse(TransactionSynchronizationManager.hasResource(cache));
    }

    @Test void changedEpochBeforeCommitRejectsQueuedLoader() {
        begin(); var old = key("epoch"); cache.put(old, page("old")); generation.incrementAndGet(); finish(true);
        assertEquals(0, nativeCache().estimatedSize());
    }

    @Test void clearRunsOnlyAfterCommit() {
        var key = key("clear"); cache.put(key, page("valid"));
        begin(); cache.clear(); assertNotNull(cache.get(key)); finish(false); assertNotNull(cache.get(key));
        begin(); cache.clear(); finish(true); assertNull(cache.get(key));
    }

    @Test void pendingTransactionPayloadCountIsBoundedAndReleased() {
        begin(); for (int i = 0; i < 100; i++) cache.put(key("pending-" + i), page("milk")); finish(true);
        assertEquals(16, nativeCache().estimatedSize());
        assertFalse(TransactionSynchronizationManager.hasResource(cache));
    }

    @Test void disabledCacheDoesNotStoreOrServe() {
        var disabled = new FoodProductSnapshotCache("foodProductSearch", new FoodProductCacheProperties().getSearch(),
                revision, new ObjectMapper(), meters, false, clock::get);
        disabled.put(key("disabled"), page("no")); assertNull(disabled.get(key("disabled")));
    }

    @Test void defaultSerializedPayloadBudgetIsBounded() {
        var properties = new FoodProductCacheProperties(); assertTrue(properties.isPayloadBudgetBounded());
        properties.getSearch().setMaxEntries(10_000); assertFalse(properties.isPayloadBudgetBounded());
        properties.getDetail().setTtl(Duration.ZERO); assertFalse(properties.getDetail().isTtlBounded());
    }

    @Test void detailTtlDoesNotExtendOnRead() {
        var detail = new FoodProductSnapshotCache("foodProductById", new FoodProductCacheProperties().getDetail(),
                revision, new ObjectMapper().findAndRegisterModules(), meters, true, clock::get);
        var product = new FoodProductDto(); product.setProductName("milk");
        var key = key("detail"); detail.put(key, product);
        clock.set(Duration.ofMinutes(4).toNanos()); assertNotNull(detail.get(key));
        clock.set(Duration.ofMinutes(5).plusSeconds(1).toNanos()); assertNull(detail.get(key));
    }

    @Test void suspendedOuterTransactionKeepsItsOwnPendingPayload() {
        begin(); var outerKey = key("outer"); cache.put(outerKey, page("outer"));
        var outerCallbacks = TransactionSynchronizationManager.getSynchronizations();
        outerCallbacks.forEach(TransactionSynchronization::suspend);
        TransactionSynchronizationManager.clearSynchronization();
        begin(); var innerKey = key("inner"); cache.put(innerKey, page("inner")); finish(true);
        assertNotNull(cache.get(innerKey)); assertNull(cache.get(outerKey));
        begin(); outerCallbacks.forEach(TransactionSynchronization::resume);
        outerCallbacks.forEach(TransactionSynchronizationManager::registerSynchronization);
        finish(false);
        assertNull(cache.get(outerKey)); assertNotNull(cache.get(innerKey));
        assertFalse(TransactionSynchronizationManager.hasResource(cache));
    }

    com.github.benmanes.caffeine.cache.Cache<?, ?> nativeCache() {
        return (com.github.benmanes.caffeine.cache.Cache<?, ?>) cache.getNativeCache();
    }
    void begin() { TransactionSynchronizationManager.initSynchronization(); TransactionSynchronizationManager.setActualTransactionActive(true); }
    void finish(boolean commit) {
        var callbacks = TransactionSynchronizationManager.getSynchronizations();
        if (commit) callbacks.forEach(TransactionSynchronization::afterCommit);
        callbacks.forEach(call -> call.afterCompletion(commit ? 0 : 1));
        TransactionSynchronizationManager.clearSynchronization(); TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    @AfterEach void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) finish(false);
        meters.close();
    }
}
