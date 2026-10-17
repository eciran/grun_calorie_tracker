package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.CacheConfig;
import com.grun.calorietracker.config.FoodProductCacheConfig;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.service.impl.FoodItemServiceImpl;
import com.grun.calorietracker.service.support.FoodProductCacheRevision;
import com.grun.calorietracker.service.support.FoodProductCacheUserScopeResolver;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.autoconfigure.cache.CacheType;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.grun.calorietracker.dto.FoodExternalSearchResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "spring.flyway.clean-disabled=true", "spring.cache.type=simple",
        "spring.jpa.show-sql=false", "spring.jpa.properties.hibernate.show_sql=false",
        "spring.datasource.hikari.maximum-pool-size=2",
        "grun.product-cache.coalescing.wait-timeout=3s"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureCache(cacheProvider = CacheType.SIMPLE)
@Import({FoodProductCacheConfig.class, CacheConfig.class, FoodProductCacheRevision.class,
        FoodProductCacheUserScopeResolver.class, FoodProductQualityIssueTracker.class,
        com.grun.calorietracker.service.support.ExternalFoodImportFence.class,
        FoodItemServiceImpl.class, com.grun.calorietracker.service.impl.FoodProductReviewServiceImpl.class,
        com.grun.calorietracker.service.impl.AdminFoodServingOptionServiceImpl.class,
        com.grun.calorietracker.service.support.FoodCategoryImportResolutionService.class,
        com.grun.calorietracker.service.support.FoodCategoryBackfillBatchService.class,
        FoodCategoryBackfillService.class,
        ProductCacheServicePostgresIntegrationTest.Dependencies.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "GRUN_RUN_PRODUCT_CACHE_POSTGRES", matches = "true")
class ProductCacheServicePostgresIntegrationTest {
    @Autowired FoodItemService service;
    @Autowired FoodProductReviewService review;
    @Autowired AdminFoodServingOptionService servingAdmin;
    @Autowired com.grun.calorietracker.service.support.FoodCategoryBackfillBatchService categoryBatch;
    @Autowired FoodCategoryBackfillService categoryBackfill;
    @org.springframework.boot.test.mock.mockito.SpyBean
    com.grun.calorietracker.service.support.FoodCategoryImportResolutionService categoryResolver;
    @Autowired JdbcTemplate jdbc;
    @Autowired MeterRegistry meters;
    @Autowired PlatformTransactionManager manager;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired com.grun.calorietracker.repository.FoodItemRepository products;
    @MockBean OpenFoodFactsService external;
    @MockBean FoodProductEvidenceService evidence;
    @MockBean CatalogPublicationService publication;
    long id;
    String barcode;

    @DynamicPropertySource static void ownedDatabase(DynamicPropertyRegistry registry) {
        String url = System.getenv("GRUN_PRODUCT_CACHE_SERVICE_TEST_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/grun_cache_service_test"))
            throw new IllegalStateException("Only the disposable localhost service-cache DB is allowed");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "r7_test");
        registry.add("spring.datasource.password", () -> "");
    }

    @BeforeEach void seed() {
        id = jdbc.queryForObject("insert into food_items(name, calories, protein, fat, carbs, is_custom, publication_status, verification_status, nutrition_reference_unit) values ('Cache Drink',60,3,3,5,false,'PUBLISHED','VERIFIED','PER_100G') returning id", Long.class);
        barcode = "994" + String.format("%010d", id);
        jdbc.update("update food_items set name=?, barcode=?, normalized_barcode=? where id=?", seedName(), barcode, barcode, id);
        jdbc.update("insert into food_item_localizations(food_item_id,language,display_name) values (?, 'TR', 'Deneme \u0130\u00e7ece\u011fi')", id);
    }

    @Test void idAndLanguageCachesHitAndNativeUpdateImmediatelyChangesGeneration() {
        double before = hits("foodProductById");
        assertEquals(seedName(), service.getFoodItemById(id, null, PreferredLanguage.EN).getProductName());
        service.getFoodItemById(id, null, PreferredLanguage.EN);
        assertEquals(before + 1, hits("foodProductById"), this::cacheDiagnostics);
        assertEquals("Deneme \u0130\u00e7ece\u011fi", service.getFoodItemById(id, null, PreferredLanguage.TR).getProductName());
        jdbc.update("update food_items set name='Changed Drink' where id=?", id);
        assertEquals("Changed Drink", service.getFoodItemById(id, null, PreferredLanguage.EN).getProductName());
        assertEquals("Deneme \u0130\u00e7ece\u011fi", service.getFoodItemById(id, null, PreferredLanguage.TR).getProductName());
    }

    @Test void barcodeCacheUsesSnapshotsAndGenerationFence() {
        var first = service.getFoodProductByBarcode(barcode);
        first.setProductName("Client mutation");
        assertEquals(seedName(), service.getFoodProductByBarcode(barcode).getProductName());
        jdbc.update("update food_items set name='Changed Barcode Drink' where id=?", id);
        assertEquals("Changed Barcode Drink", service.getFoodProductByBarcode(barcode).getProductName());
    }

    @Test void searchCacheHitsAndRejectsPriorPublicationState() {
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery(seedName());
        var first = service.searchFoodItems(criteria, 0, 25);
        assertTrue(first.getContent().stream().anyMatch(p -> p.getId().equals(id)));
        double before = hits("foodProductSearch"); service.searchFoodItems(criteria, 0, 25);
        assertEquals(before + 1, hits("foodProductSearch"), this::cacheDiagnostics);
        jdbc.update("update food_items set publication_status='INTERNAL_REVIEW' where id=?", id);
        assertTrue(service.searchFoodItems(criteria, 0, 25).getContent().stream().noneMatch(p -> p.getId().equals(id)));
    }

    @Test void readInsideRolledBackWriterCannotPoisonCache() {
        service.getFoodItemById(id, null, PreferredLanguage.EN);
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            jdbc.update("update food_items set name='Pending Name' where id=?", id);
            assertEquals("Pending Name", service.getFoodItemById(id, null, PreferredLanguage.EN).getProductName());
            status.setRollbackOnly();
        });
        assertEquals(seedName(), service.getFoodItemById(id, null, PreferredLanguage.EN).getProductName());
    }

    @RepeatedTest(5) void eightConcurrentNegativeSearchesCoalesceBeforeOpeningTheirTransactions() throws Exception {
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery("missing-cache-" + id);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var providerCalls = new AtomicInteger();
        when(external.searchProductsOutcome(any())).thenAnswer(call -> {
            providerCalls.incrementAndGet(); assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            entered.countDown(); assertTrue(release.await(4, TimeUnit.SECONDS));
            return FoodExternalSearchResult.success(List.of());
        });
        double before = coordinationWaits();
        var executor = Executors.newFixedThreadPool(8); var start = new CyclicBarrier(8);
        var results = new java.util.ArrayList<Future<com.grun.calorietracker.dto.FoodProductSearchPageDto>>();
        try {
            for (int i = 0; i < 8; i++) results.add(executor.submit(() -> {
                start.await(3, TimeUnit.SECONDS); return service.searchFoodItems(criteria, 0, 25);
            }));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (coordinationWaits() - before < 7 && System.nanoTime() < deadline) Thread.sleep(5);
            assertEquals(7, coordinationWaits() - before, "Waiters must not exhaust the two-connection pool");
            release.countDown();
            for (var future : results) {
                var page = future.get(5, TimeUnit.SECONDS);
                assertTrue(page.getContent().isEmpty()); assertEquals(0L, page.getTotalElements());
            }
            assertEquals(1, providerCalls.get());
        } finally {
            release.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void providerFailureIsNotCachedAndNextCallCanRecover() {
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery("missing-recovery-" + id);
        when(external.searchProductsOutcome(any())).thenReturn(FoodExternalSearchResult.unavailable(),
                FoodExternalSearchResult.success(List.of()));
        var failed = service.searchFoodItems(criteria, 0, 25);
        assertTrue(failed.getContent().isEmpty()); assertFalse(failed.isCacheable());
        var recovered = service.searchFoodItems(criteria, 0, 25); assertTrue(recovered.isCacheable());
        service.searchFoodItems(criteria, 0, 25);
        verify(external, times(2)).searchProductsOutcome(any());
    }

    @Test void negativeResultDoesNotHideANewlyPublishedProduct() {
        String name = "Fresh Cache Unique " + id;
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery(name);
        when(external.searchProductsOutcome(any())).thenReturn(FoodExternalSearchResult.success(List.of()));
        assertTrue(service.searchFoodItems(criteria, 0, 25).getContent().isEmpty());
        Long newId = jdbc.queryForObject("insert into food_items(name,calories,protein,fat,carbs,is_custom,publication_status,verification_status) values (?,60,3,3,5,false,'PUBLISHED','VERIFIED') returning id", Long.class, name);
        assertTrue(service.searchFoodItems(criteria, 0, 25).getContent().stream().anyMatch(p -> newId.equals(p.getId())));
        verify(external, times(1)).searchProductsOutcome(any());
    }

    @Test void warmedPrivateProductCacheDoesNotLeakToOtherOwnersAnonymousOrBarcodeCalls() {
        String owner = "cache-owner-" + id + "@example.invalid";
        String other = "cache-other-" + id + "@example.invalid";
        Long ownerId = jdbc.queryForObject("insert into users(email,role) values (?,'STANDARD') returning id", Long.class, owner);
        jdbc.update("insert into users(email,role) values (?,'STANDARD')", other);
        jdbc.update("update food_items set is_custom=true,publication_status='PRIVATE_USER',created_by_user_id=? where id=?", ownerId, id);
        assertEquals(id, service.getFoodItemById(id, owner, PreferredLanguage.EN).getId());
        assertEquals(id, service.getFoodItemById(id, owner.toUpperCase(java.util.Locale.ROOT), PreferredLanguage.EN).getId());
        assertThrows(com.grun.calorietracker.exception.ProductNotFoundException.class,
                () -> service.getFoodItemById(id, other, PreferredLanguage.EN));
        assertThrows(com.grun.calorietracker.exception.ProductNotFoundException.class,
                () -> service.getFoodItemById(id, null, PreferredLanguage.EN));
        assertThrows(com.grun.calorietracker.exception.ProductNotFoundException.class,
                () -> service.getFoodProductByBarcode(barcode));
        assertEquals("Deneme \u0130\u00e7ece\u011fi", service.getFoodItemById(id, owner, PreferredLanguage.TR).getProductName());
    }

    @Test void importedFallbackIsFilteredSortedAndCacheableOnlyAfterCommit() {
        String name = "Fallback Drink " + id;
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery(name);
        criteria.setSortBy("calories"); criteria.setSortOrder("asc"); criteria.setMaxCalories(80.0);
        var high = externalProduct(name + " High", testGtin(id), 100);
        var low = externalProduct(name + " Low", testGtin(id + 2000000), 60);
        when(external.searchProductsOutcome(any())).thenReturn(FoodExternalSearchResult.success(List.of(high, low, low)));
        when(publication.publishNew(any(), any(), any(), any())).thenAnswer(call -> {
            com.grun.calorietracker.entity.FoodItemEntity item = call.getArgument(0);
            item.setPublicationStatus(com.grun.calorietracker.enums.CatalogPublicationStatus.PUBLISHED);
            return products.saveAndFlush(item);
        });
        var first = service.searchFoodItems(criteria, 0, 1);
        assertEquals(1L, first.getTotalElements()); assertEquals(60.0, first.getContent().get(0).getCalories());
        double before = hits("foodProductSearch");
        assertEquals(first.getContent().get(0).getId(), service.searchFoodItems(criteria, 0, 1).getContent().get(0).getId());
        assertEquals(first.getContent().get(0).getId(), service.searchFoodItems(criteria, 0, 1).getContent().get(0).getId());
        assertEquals(before + 1, hits("foodProductSearch"), this::cacheDiagnostics);
        verify(external, times(1)).searchProductsOutcome(any());
        verify(publication, times(2)).publishNew(any(), any(), any(), any());
        var beyond = service.searchFoodItems(criteria, 1, 1);
        assertTrue(beyond.getContent().isEmpty()); assertEquals(1L, beyond.getTotalElements());
    }

    @Test void lateEvidenceFailureRollsBackImportedProductAndDoesNotPoisonCache() {
        String name = "Rollback Fallback " + id;
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery(name);
        String sourceBarcode = testGtin(id);
        when(external.searchProductsOutcome(any())).thenReturn(
                FoodExternalSearchResult.success(List.of(externalProduct(name, sourceBarcode, 60))));
        when(publication.publishNew(any(), any(), any(), any())).thenAnswer(call -> {
            com.grun.calorietracker.entity.FoodItemEntity item = call.getArgument(0);
            item.setPublicationStatus(com.grun.calorietracker.enums.CatalogPublicationStatus.PUBLISHED);
            return products.saveAndFlush(item);
        });
        doThrow(new IllegalStateException("Simulated late evidence failure"))
                .when(evidence).recordImportEvidence(any(), any(), any(), any(), any());
        long before = products.count();
        assertThrows(IllegalStateException.class, () -> service.searchFoodItems(criteria, 0, 25));
        assertEquals(before, products.count()); assertTrue(products.findByNormalizedBarcode(sourceBarcode).isEmpty());
        reset(evidence);
        var recovered = service.searchFoodItems(criteria, 0, 25);
        assertEquals(1L, recovered.getTotalElements()); assertEquals(60.0, recovered.getContent().get(0).getCalories());
        assertEquals(before + 1, products.count());
        verify(external, times(2)).searchProductsOutcome(any());
    }

    private com.grun.calorietracker.dto.FoodProductDto externalProduct(String name, String gtin, double calories) {
        var source = new com.grun.calorietracker.dto.FoodProductDto();
        source.setProductName(name); source.setBarcode(gtin); source.setCalories(calories);
        source.setProtein(3.0); source.setFat(3.0); source.setCarbs(5.0);
        source.setBrand("Acme"); source.setMarketRegion(com.grun.calorietracker.enums.MarketRegion.UK_IE);
        source.setNutritionReferenceUnit(com.grun.calorietracker.enums.FoodNutritionReferenceUnit.PER_100G);
        return source;
    }

    @RepeatedTest(3) void distinctQueryKeysImportTheSameBarcodeOnce() throws Exception {
        String name = "Concurrent Shared Drink cohort" + id;
        String gtin = testGtin(id);
        var first = new FoodSearchCriteriaDto(); first.setQuery(name);
        var second = new FoodSearchCriteriaDto(); second.setQuery("Drink Shared Concurrent cohort" + id);
        var providerBarrier = new CyclicBarrier(2);
        when(external.searchProductsOutcome(any())).thenAnswer(call -> {
            providerBarrier.await(4, TimeUnit.SECONDS);
            return FoodExternalSearchResult.success(List.of(externalProduct(name, gtin, 60)));
        });
        persistPublicationsSlowly();
        var results = concurrently(() -> service.searchFoodItems(first, 0, 25).getContent().get(0).getId(),
                () -> service.searchFoodItems(second, 0, 25).getContent().get(0).getId());
        assertEquals(results.get(0), results.get(1));
        assertEquals(1, jdbc.queryForObject("select count(*) from food_items where normalized_barcode=?", Integer.class, gtin));
        verify(publication, times(1)).publishNew(any(), any(), any(), any());
        verify(evidence, times(1)).recordImportEvidence(any(), any(), any(), any(), any());
    }

    @Test void barcodeLookupAndSearchShareTheImportIdentityFence() throws Exception {
        String name = "Barcode Race Drink cohort" + id;
        String gtin = testGtin(id);
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery(name);
        var source = externalProduct(name, gtin, 60);
        var providerBarrier = new CyclicBarrier(2);
        when(external.searchProductsOutcome(any())).thenAnswer(call -> {
            providerBarrier.await(4, TimeUnit.SECONDS); return FoodExternalSearchResult.success(List.of(source));
        });
        when(external.getProductByBarcode(gtin)).thenAnswer(call -> {
            providerBarrier.await(4, TimeUnit.SECONDS); return java.util.Optional.of(source);
        });
        persistPublicationsSlowly();
        var results = concurrently(() -> service.getFoodProductByBarcode(gtin).getId(),
                () -> service.searchFoodItems(criteria, 0, 25).getContent().get(0).getId());
        assertEquals(results.get(0), results.get(1));
        assertEquals(1, jdbc.queryForObject("select count(*) from food_items where normalized_barcode=?", Integer.class, gtin));
        verify(publication, times(1)).publishNew(any(), any(), any(), any());
    }

    @Test void oppositeFeedOrderRetainsBothBarcodesWithoutDeadlockOrOverwrite() throws Exception {
        String name = "Opposite Batch Drink cohort" + id;
        String a = testGtin(id), b = testGtin(id + 1000000);
        var first = new FoodSearchCriteriaDto(); first.setQuery(name);
        var second = new FoodSearchCriteriaDto(); second.setQuery("Drink Batch Opposite cohort" + id);
        var providerBarrier = new CyclicBarrier(2);
        var calls = new AtomicInteger();
        when(external.searchProductsOutcome(any())).thenAnswer(call -> {
            int order = calls.getAndIncrement(); providerBarrier.await(4, TimeUnit.SECONDS);
            var x = externalProduct(name + " A", a, 60); var y = externalProduct(name + " B", b, 70);
            return FoodExternalSearchResult.success(order == 0 ? List.of(x, y) : List.of(y, x));
        });
        persistPublicationsSlowly();
        var results = concurrently(() -> service.searchFoodItems(first, 0, 25).getTotalElements(),
                () -> service.searchFoodItems(second, 0, 25).getTotalElements());
        assertEquals(List.of(2L, 2L), results);
        assertEquals(60.0, products.findByNormalizedBarcode(a).orElseThrow().getCalories());
        assertEquals(70.0, products.findByNormalizedBarcode(b).orElseThrow().getCalories());
        verify(publication, times(2)).publishNew(any(), any(), any(), any());
    }

    @Test void simultaneousCsvImportsReuseOneBarcodeIdentity() throws Exception {
        var importer = new com.grun.calorietracker.service.impl.FoodProductImportServiceImpl(products,
                context.getBean(com.grun.calorietracker.repository.FoodItemLocalizationRepository.class),
                context.getBean(com.grun.calorietracker.repository.FoodItemSearchAliasRepository.class),
                context.getBean(com.grun.calorietracker.repository.FoodItemServingOptionRepository.class),
                context.getBean(com.grun.calorietracker.repository.FoodItemServingOptionLocalizationRepository.class),
                mock(FoodProductQualityIssueTracker.class), evidence, new ObjectMapper());
        org.springframework.test.util.ReflectionTestUtils.setField(importer, "externalFoodImportFence",
                context.getBean(com.grun.calorietracker.service.support.ExternalFoodImportFence.class));
        String gtin = testGtin(id);
        String csv = "barcode,name,calories,protein,fat,carbs,market_region,data_source,nutrition_reference_unit\n"
                + gtin + ",CSV Shared Drink,60,3,3,5,UK_IE,OPEN_FOOD_FACTS,PER_100G\n";
        var start = new CyclicBarrier(2);
        Callable<com.grun.calorietracker.dto.FoodProductImportResultDto> action = () -> {
            start.await(4, TimeUnit.SECONDS);
            return new TransactionTemplate(manager).execute(status -> importer.importCsv(
                    new org.springframework.mock.web.MockMultipartFile("file", "fixture.csv", "text/csv",
                            csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "fixture@example.invalid"));
        };
        var results = concurrently(action, action);
        for (var result : results) { assertEquals(1, result.getSavedRows(), result.getErrors().toString()); }
        assertEquals(1, results.stream().mapToInt(r -> r.getInsertedRows()).sum());
        assertEquals(1, results.stream().mapToInt(r -> r.getUpdatedRows()).sum());
        assertEquals(1, jdbc.queryForObject("select count(*) from food_items where normalized_barcode=?", Integer.class, gtin));
    }

    @Test void importFenceTimeoutRollsBackAndNextCallRecovers() throws Exception {
        String name = "Timeout Fence Drink cohort" + id;
        String gtin = testGtin(id);
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery(name);
        when(external.searchProductsOutcome(any())).thenReturn(
                FoodExternalSearchResult.success(List.of(externalProduct(name, gtin, 60))));
        persistPublicationsSlowly();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        Future<?> holder = executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            context.getBean(com.grun.calorietracker.service.support.ExternalFoodImportFence.class).acquire();
            entered.countDown();
            try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
        }));
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            assertThrows(org.springframework.dao.DataAccessException.class, () -> service.searchFoodItems(criteria, 0, 25));
            assertTrue(products.findByNormalizedBarcode(gtin).isEmpty());
            verify(publication, never()).publishNew(any(), any(), any(), any());
        } finally {
            release.countDown(); holder.get(4, TimeUnit.SECONDS);
            executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertEquals(1, service.searchFoodItems(criteria, 0, 25).getContent().size());
        verify(external, times(2)).searchProductsOutcome(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void adminReviewWaitsBeforeReadAndPreservesCommittedOrRolledBackImportFields(boolean commit) throws Exception {
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(review));
        service.getFoodItemById(id, null, PreferredLanguage.EN);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        var holder = executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            context.getBean(com.grun.calorietracker.service.support.ExternalFoodImportFence.class).acquire();
            jdbc.update("update food_items set protein=4 where id=?", id);
            entered.countDown(); awaitRelease(release);
            if (!commit) status.setRollbackOnly();
        }));
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            var request = new com.grun.calorietracker.dto.FoodProductReviewRequestDto();
            request.setProductName("Reviewed Drink");
            var writer = executor.submit(() -> review.updateProductReview(id, request, "fixture@example.invalid"));
            awaitFenceWaiter(); assertFalse(writer.isDone()); release.countDown(); holder.get(4, TimeUnit.SECONDS);
            assertEquals("Reviewed Drink", writer.get(4, TimeUnit.SECONDS).getProductName());
            var current = service.getFoodItemById(id, null, PreferredLanguage.EN);
            assertEquals("Reviewed Drink", current.getProductName());
            assertEquals(commit ? 4.0 : 3.0, current.getProtein()); assertEquals(60.0, current.getCalories());
        } finally {
            release.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void adminReviewTimeoutLeavesNoMutationOrAuditAndRetrySucceeds() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        var holder = executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            context.getBean(com.grun.calorietracker.service.support.ExternalFoodImportFence.class).acquire();
            entered.countDown(); awaitRelease(release);
        }));
        var request = new com.grun.calorietracker.dto.FoodProductReviewRequestDto();
        request.setProductName("After Timeout");
        int audits = jdbc.queryForObject("select count(*) from food_product_review_audits where food_item_id=?", Integer.class, id);
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> review.updateProductReview(id, request, "fixture@example.invalid"));
            assertEquals(seedName(), jdbc.queryForObject("select name from food_items where id=?", String.class, id));
            assertEquals(audits, jdbc.queryForObject("select count(*) from food_product_review_audits where food_item_id=?", Integer.class, id));
        } finally {
            release.countDown(); holder.get(4, TimeUnit.SECONDS);
            executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertEquals("After Timeout", review.updateProductReview(id, request, "fixture@example.invalid").getProductName());
    }

    @Test void adminBarcodeWaitsForRealCsvImportThenRejectsReassignment() throws Exception {
        String gtin = testGtin(id);
        var importer = new com.grun.calorietracker.service.impl.FoodProductImportServiceImpl(products,
                context.getBean(com.grun.calorietracker.repository.FoodItemLocalizationRepository.class),
                context.getBean(com.grun.calorietracker.repository.FoodItemSearchAliasRepository.class),
                context.getBean(com.grun.calorietracker.repository.FoodItemServingOptionRepository.class),
                context.getBean(com.grun.calorietracker.repository.FoodItemServingOptionLocalizationRepository.class),
                mock(FoodProductQualityIssueTracker.class), evidence, new ObjectMapper());
        org.springframework.test.util.ReflectionTestUtils.setField(importer, "externalFoodImportFence",
                context.getBean(com.grun.calorietracker.service.support.ExternalFoodImportFence.class));
        String csv = "barcode,name,calories,protein,fat,carbs,market_region,data_source,nutrition_reference_unit\n"
                + gtin + ",CSV Identity Drink,60,3,3,5,UK_IE,OPEN_FOOD_FACTS,PER_100G\n";
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        var holder = executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            var imported = importer.importCsv(new org.springframework.mock.web.MockMultipartFile("file", "fixture.csv", "text/csv",
                    csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "fixture@example.invalid");
            assertEquals(1, imported.getInsertedRows(), imported.getErrors().toString());
            entered.countDown(); awaitRelease(release);
        }));
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            var writer = executor.submit(() -> review.updateProductBarcode(id,
                    new com.grun.calorietracker.dto.FoodProductBarcodeUpdateRequestDto(gtin, "Verified test barcode collision"),
                    "fixture@example.invalid"));
            awaitFenceWaiter(); assertFalse(writer.isDone()); release.countDown(); holder.get(4, TimeUnit.SECONDS);
            var failure = assertThrows(ExecutionException.class, () -> writer.get(4, TimeUnit.SECONDS));
            assertInstanceOf(IllegalArgumentException.class, failure.getCause());
            assertTrue(failure.getCause().getMessage().contains("already assigned"));
            assertEquals(barcode, products.findById(id).orElseThrow().getNormalizedBarcode());
            assertNotEquals(id, products.findByNormalizedBarcode(gtin).orElseThrow().getId());
            assertEquals(1, jdbc.queryForObject("select count(*) from food_items where normalized_barcode=?", Integer.class, gtin));
        } finally {
            release.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void actualSearchCacheTracksAdminAliasesAndNativeEnTrLabelChanges() {
        var request = new com.grun.calorietracker.dto.FoodSearchAliasRequestDto();
        request.setAlias("controlledalias" + id); request.setLanguage(PreferredLanguage.TR);
        var alias = review.addProductSearchAlias(id, request, "fixture@example.invalid");
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery(request.getAlias());
        criteria.setPreferredLanguage(PreferredLanguage.TR);
        var first = service.searchFoodItems(criteria, 0, 25);
        assertEquals(id, first.getContent().get(0).getId());
        double before = hits("foodProductSearch"); service.searchFoodItems(criteria, 0, 25);
        assertEquals(before + 1, hits("foodProductSearch"));
        jdbc.update("update food_item_localizations set display_name='Yeni Ad', short_display_name='Yeni Ad' where food_item_id=? and language='TR'", id);
        assertEquals("Yeni Ad", service.searchFoodItems(criteria, 0, 25).getContent().get(0).getDisplayName());
        assertEquals("Deneme \u0130\u00e7ece\u011fi", first.getContent().get(0).getDisplayName());
        assertEquals(seedName(), service.getFoodItemById(id, null, PreferredLanguage.EN).getProductName());
        review.updateProductSearchAliasStatus(id, alias.getId(), false, "fixture@example.invalid");
        when(external.searchProductsOutcome(any())).thenReturn(FoodExternalSearchResult.success(List.of()));
        assertTrue(service.searchFoodItems(criteria, 0, 25).getContent().isEmpty());
        review.updateProductSearchAliasStatus(id, alias.getId(), true, "fixture@example.invalid");
        assertEquals(id, service.searchFoodItems(criteria, 0, 25).getContent().get(0).getId());
    }

    @Test void actualSearchCacheTracksCategoryMembershipAndServingVisibilityWithoutNutritionRewrite() {
        Long root = jdbc.queryForObject("insert into food_categories(slug,name_en,name_tr,active,sort_order,created_at,updated_at) values (?,'Root','Ana',true,0,now(),now()) returning id", Long.class, "cache-root-" + id);
        Long child = jdbc.queryForObject("insert into food_categories(slug,name_en,name_tr,parent_id,active,sort_order,created_at,updated_at) values (?,'Child','Alt',?,true,0,now(),now()) returning id", Long.class, "cache-child-" + id, root);
        jdbc.update("insert into food_item_categories(food_item_id,category_id,primary_category,assignment_source,reviewed,created_at,updated_at) values (?,?,true,'ADMIN',true,now(),now())", id, child);
        Long option = jdbc.queryForObject("insert into food_item_serving_options(food_item_id,label,unit_type,quantity,gram_weight,is_default,source,quality_status,user_visible) values (?,'Test slice','SLICE',1,30,true,'ADMIN','VERIFIED',true) returning id", Long.class, id);
        var criteria = new FoodSearchCriteriaDto(); criteria.setQuery(seedName()); criteria.setCategoryId(root);
        var first = service.searchFoodItems(criteria, 0, 25);
        assertEquals(1, first.getTotalElements());
        assertEquals(List.of(option), first.getContent().get(0).getServingOptions().stream().map(o -> o.getId()).toList());
        double before = hits("foodProductSearch"); service.searchFoodItems(criteria, 0, 25);
        assertEquals(before + 1, hits("foodProductSearch"));
        jdbc.update("update food_item_serving_options set user_visible=false where id=?", option);
        assertTrue(service.searchFoodItems(criteria, 0, 25).getContent().get(0).getServingOptions().isEmpty());
        assertEquals(1, first.getContent().get(0).getServingOptions().size());
        jdbc.update("delete from food_item_categories where food_item_id=? and category_id=?", id, child);
        assertTrue(service.searchFoodItems(criteria, 0, 25).getContent().isEmpty());
        assertEquals(60.0, products.findById(id).orElseThrow().getCalories());
        assertEquals(30.0, jdbc.queryForObject("select gram_weight from food_item_serving_options where id=?", Double.class, option));
    }

    private void awaitFenceWaiter() throws InterruptedException {
        // Independent observer avoids consuming either of the two worker-pool connections.
        var observer = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                System.getenv("GRUN_PRODUCT_CACHE_SERVICE_TEST_URL"), "r7_test", ""));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (observer.queryForObject("select count(*) from pg_locks where locktype='advisory' and classid=17491 and objid=310 and not granted", Integer.class) > 0) return;
            Thread.sleep(10);
        }
        fail("The admin writer did not wait on the common import fence before its first read");
    }

    private static void awaitRelease(CountDownLatch release) {
        try { assertTrue(release.await(12, TimeUnit.SECONDS)); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
    }

    @Test void concurrentEquivalentServingCreatesYieldOneOptionAndTwoLabels() throws Exception {
        var start = new CyclicBarrier(2);
        Callable<Boolean> action = () -> {
            start.await(3, TimeUnit.SECONDS);
            try { servingAdmin.create(id, servingRequest(100.0), "fixture@example.invalid"); return true; }
            catch (IllegalArgumentException duplicate) { assertTrue(duplicate.getMessage().contains("equivalent")); return false; }
        };
        var results = concurrently(action, action);
        assertEquals(1L, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, jdbc.queryForObject("select count(*) from food_item_serving_options where food_item_id=?", Integer.class, id));
        assertEquals(2, jdbc.queryForObject("select count(*) from food_item_serving_option_localizations l join food_item_serving_options o on o.id=l.serving_option_id where o.food_item_id=?", Integer.class, id));
        assertEquals(60.0, products.findById(id).orElseThrow().getCalories());
    }

    @Test void concurrentDistinctDefaultsLeaveExactlyOneDefaultAndRefreshDetailCache() throws Exception {
        var old = service.getFoodItemById(id, null, PreferredLanguage.EN);
        var start = new CyclicBarrier(2);
        concurrently(() -> { start.await(3, TimeUnit.SECONDS); return servingAdmin.create(id, servingRequest(100.0), "fixture@example.invalid"); },
                () -> { start.await(3, TimeUnit.SECONDS); return servingAdmin.create(id, servingRequest(150.0), "fixture@example.invalid"); });
        assertEquals(2, jdbc.queryForObject("select count(*) from food_item_serving_options where food_item_id=?", Integer.class, id));
        assertEquals(1, jdbc.queryForObject("select count(*) from food_item_serving_options where food_item_id=? and is_default", Integer.class, id));
        var fresh = service.getFoodItemById(id, null, PreferredLanguage.EN);
        assertEquals(2, fresh.getServingOptions().size()); assertNotNull(fresh.getDefaultServingOptionId());
        assertTrue(old.getServingOptions().isEmpty()); assertEquals(60.0, fresh.getCalories());
    }

    @Test void categoryBatchLateFailureRollsBackAndCanResumeSafely() {
        prepareCategoryCandidate();
        var once = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (!once.getAndSet(true)) throw new IllegalStateException("Simulated late category batch failure");
            return result;
        }).when(resolverSpy()).resolveAfterImport(any(), any());
        assertThrows(IllegalStateException.class, () -> categoryBatch.process(id - 1, 1, "fixture@example.invalid"));
        assertEquals(0, jdbc.queryForObject("select count(*) from food_item_categories where food_item_id=?", Integer.class, id));
        assertEquals(0, jdbc.queryForObject("select count(*) from food_product_quality_issues where food_item_id=?", Integer.class, id));
        var retry = categoryBatch.process(id - 1, 1, "fixture@example.invalid");
        assertEquals(1, retry.resolution().assignedProducts()); assertEquals(id, retry.nextId());
        assertEquals(1, jdbc.queryForObject("select count(*) from food_item_categories where food_item_id=? and primary_category", Integer.class, id));
        assertEquals(60.0, products.findById(id).orElseThrow().getCalories());
    }

    @Test void categoryBatchWaitsBeforeInventoryAndPreservesConcurrentReviewedPrimary() throws Exception {
        long category = prepareCategoryCandidate();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        var holder = executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            context.getBean(com.grun.calorietracker.service.support.ExternalFoodImportFence.class).acquire();
            jdbc.update("insert into food_item_categories(food_item_id,category_id,primary_category,assignment_source,reviewed,created_at,updated_at) values (?,?,true,'ADMIN',true,now(),now())", id, category);
            entered.countDown(); awaitRelease(release);
        }));
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            var writer = executor.submit(() -> categoryBatch.process(id - 1, 1, "fixture@example.invalid"));
            awaitFenceWaiter(); release.countDown(); holder.get(4, TimeUnit.SECONDS);
            assertEquals(0, writer.get(4, TimeUnit.SECONDS).selected());
            assertEquals(1, jdbc.queryForObject("select count(*) from food_item_categories where food_item_id=? and assignment_source='ADMIN' and reviewed", Integer.class, id));
        } finally { release.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    @Test void categoryOrchestratorUsesSeparateBoundedTransactionsAndResumeCursor() {
        prepareCategoryCandidate();
        long other = jdbc.queryForObject("insert into food_items(name,is_custom,data_source) values ('Next Category Fixture',false,'OPEN_FOOD_FACTS') returning id", Long.class);
        jdbc.update("insert into food_item_source_categories(food_item_id,category_tag) select ?,category_tag from food_item_source_categories where food_item_id=?", other, id);
        var transactions = new java.util.ArrayList<Long>();
        doAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            transactions.add(jdbc.queryForObject("select txid_current()", Long.class));
            return invocation.callRealMethod();
        }).when(resolverSpy()).resolveAfterImport(any(), any());
        var result = categoryBackfill.backfill(1, 2, id - 1, "fixture@example.invalid");
        assertEquals(2, result.processedBatches()); assertEquals(2, result.assignedProducts());
        assertEquals(other, result.lastProcessedId()); assertEquals(2, new java.util.HashSet<>(transactions).size());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    private long prepareCategoryCandidate() {
        var mapping = jdbc.queryForMap("select normalized_source_tag,category_id from food_category_source_mappings where data_source='OPEN_FOOD_FACTS' and status='ACTIVE' and market_region is null order by id limit 1");
        jdbc.update("update food_items set data_source='OPEN_FOOD_FACTS' where id=?", id);
        jdbc.update("insert into food_item_source_categories(food_item_id,category_tag) values (?,?)", id, mapping.get("normalized_source_tag"));
        return ((Number)mapping.get("category_id")).longValue();
    }

    private com.grun.calorietracker.service.support.FoodCategoryImportResolutionService resolverSpy() {
        // Stubbing the target must not invoke the MANDATORY transaction advice before the test begins.
        return org.springframework.test.util.AopTestUtils.getUltimateTargetObject(categoryResolver);
    }

    private com.grun.calorietracker.dto.AdminFoodServingOptionUpsertRequestDto servingRequest(double grams) {
        var request = new com.grun.calorietracker.dto.AdminFoodServingOptionUpsertRequestDto();
        request.setLabel("Reference portion " + grams); request.setQuantity(1.0); request.setGramWeight(grams);
        request.setUnitType(com.grun.calorietracker.enums.FoodServingOptionUnit.PIECE); request.setDefaultOption(true);
        request.setLocalizations(List.of(
                new com.grun.calorietracker.dto.AdminFoodServingOptionUpsertRequestDto.Localization(PreferredLanguage.EN, "Reference portion " + grams),
                new com.grun.calorietracker.dto.AdminFoodServingOptionUpsertRequestDto.Localization(PreferredLanguage.TR, "Referans porsiyon " + grams)));
        return request;
    }

    private void persistPublicationsSlowly() {
        when(publication.publishNew(any(), any(), any(), any())).thenAnswer(call -> {
            Thread.sleep(150); // Widen the missing-row race before the unique-index write.
            com.grun.calorietracker.entity.FoodItemEntity item = call.getArgument(0);
            item.setPublicationStatus(com.grun.calorietracker.enums.CatalogPublicationStatus.PUBLISHED);
            return products.saveAndFlush(item);
        });
    }

    private <T> List<T> concurrently(Callable<T> first, Callable<T> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(first); var b = executor.submit(second);
            return List.of(a.get(12, TimeUnit.SECONDS), b.get(12, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    private String seedName() { return "Cache Drink cohort" + id; }

    private String testGtin(long suffix) {
        String body = "990" + String.format("%09d", suffix);
        int sum = 0;
        for (int i = 0; i < body.length(); i++) sum += (body.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        return body + ((10 - sum % 10) % 10);
    }

    double coordinationWaits() {
        var counter = meters.find("grun.product.cache.coalescing").tag("result", "wait").counter();
        return counter == null ? 0 : counter.count();
    }

    double hits(String name) {
        var counter = meters.find("grun.product.cache.access").tag("cache", name).tag("result", "hit").counter();
        return counter == null ? 0 : counter.count();
    }
    String cacheDiagnostics() {
        return meters.getMeters().stream().filter(m -> m.getId().getName().startsWith("grun.product.cache"))
                .map(m -> m.getId() + "=" + m.measure()).toList().toString();
    }
    @TestConfiguration static class Dependencies {
        @Bean ObjectMapper cacheObjectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean MeterRegistry cacheMeters() { return new SimpleMeterRegistry(); }
    }
}
