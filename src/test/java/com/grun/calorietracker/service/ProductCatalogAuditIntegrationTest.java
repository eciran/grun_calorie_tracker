package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.ProductCatalogAuditProperties;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.impl.ProductCatalogAuditService;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.jpa.show-sql=false", "spring.jpa.properties.hibernate.show_sql=false"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProductCatalogAuditIntegrationTest {
    @Autowired ProductCatalogAuditStateRepository states;
    @Autowired ProductCatalogAuditItemRepository items;
    @Autowired ProductQualityScanRunRepository runs;
    @Autowired FoodItemRepository foods;
    @Autowired FoodItemServingOptionRepository servings;
    @Autowired FoodItemLocalizationRepository localizations;
    @Autowired ProductQualitySuggestionRepository suggestionRepository;
    @Autowired FoodProductQualityIssueRepository issues;
    @Autowired PlatformTransactionManager transactions;
    ProductQualitySuggestionService suggestions;
    ProductCatalogAuditProperties properties;
    ProductCatalogAuditService service;

    @BeforeEach
    void setup() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            items.deleteAllInBatch();
            states.deleteAllInBatch();
            suggestionRepository.deleteAllInBatch();
            issues.deleteAllInBatch();
            servings.deleteAllInBatch();
            localizations.deleteAllInBatch();
            foods.deleteAllInBatch();
            runs.deleteAllInBatch();
            states.saveAndFlush(new ProductCatalogAuditStateEntity());
        });
        suggestions = mock(ProductQualitySuggestionService.class);
        properties = new ProductCatalogAuditProperties();
        properties.setBatchSize(2);
        service = newWorker();
    }

    ProductCatalogAuditService newWorker() {
        var worker = new ProductCatalogAuditService(states, items, runs, foods, servings, localizations,
                new FoodProductQualityIssueTracker(issues), suggestions, suggestionRepository,
                transactions, properties, new ObjectMapper().findAndRegisterModules());
        at(worker, "2026-10-11T11:00:00Z");
        return worker;
    }

    void at(ProductCatalogAuditService worker, String instant) {
        ReflectionTestUtils.setField(worker, "clock", Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    FoodItemEntity food(String name) {
        var food = new FoodItemEntity();
        food.setName(name);
        food.setDisplayName(name);
        food.setIsCustom(false);
        food.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        food.setPreparationState(FoodPreparationState.RAW);
        food.setDataSource(FoodDataSource.USDA_FOODDATA);
        food.setSourceDataType("Foundation");
        food.setMarketRegion(MarketRegion.GLOBAL);
        food.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100G);
        food.setNutritionBasis(FoodNutritionBasis.SOURCE_REPORTED);
        food.setPublicationStatus(CatalogPublicationStatus.PUBLISHED);
        food.setSourceKey("audit:" + name);
        food.setCalories(100.0);
        food.setProtein(5.0);
        food.setFat(2.0);
        food.setCarbs(15.0);
        food.setFiber(1.0);
        food.setSugar(1.0);
        food.setSodium(0.001);
        food.setCalcium(1.0);
        food.setConfidenceScore(90);
        food.setServingSizeGrams(100.0);
        food.setServingUnit("GRAM");
        var saved = foods.saveAndFlush(food);
        for (var language : List.of(PreferredLanguage.EN, PreferredLanguage.TR)) {
            var localization = new FoodItemLocalizationEntity();
            localization.setFoodItem(saved);
            localization.setLanguage(language);
            localization.setDisplayName(name);
            localization.setShortDisplayName(name);
            localizations.saveAndFlush(localization);
        }
        return saved;
    }

    @Test
    void localizationChangesAreReviewedAndFingerprintChangesWithoutRewritingFacts() {
        var product = food("Milk");
        service.start("admin@test");
        service.step();
        var before = items.findById(product.getId()).orElseThrow().getInputFingerprint();
        var tr = localizations.findByFoodItemIdAndLanguage(product.getId(), PreferredLanguage.TR).orElseThrow();
        tr.setDisplayName("PISMIS SUT");
        localizations.saveAndFlush(tr);
        service.start("admin@test");
        service.step();
        var item = items.findById(product.getId()).orElseThrow();
        assertNotEquals(before, item.getInputFingerprint());
        assertTrue(item.getFindingsJson().contains("TR_DIACRITICS_REVIEW"));
        assertEquals("PISMIS SUT", localizations.findById(tr.getId()).orElseThrow().getDisplayName());
        assertEquals(100.0, foods.findById(product.getId()).orElseThrow().getCalories());
    }

    @Test
    void sourceSubtypeConflictIsReviewOnlyAndChangingIdentityChangesFingerprint() {
        var product = food("Banana");
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        foods.saveAndFlush(product);
        service.start("admin@test");
        service.step();
        var first = items.findById(product.getId()).orElseThrow();
        assertTrue(first.getFindingsJson().contains("USDA_CLASSIFICATION_CONFLICT"));
        assertEquals(FoodCatalogType.BRANDED_PRODUCT, foods.findById(product.getId()).orElseThrow().getCatalogType());
        String hash = first.getInputFingerprint();
        product.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        product.setFoodConceptKey("banana");
        product.setCompositionVariantKey("edible_flesh");
        product.setCanonicalFoodKey(com.grun.calorietracker.service.support.FoodProductCanonicalKeyRules.resolve(product));
        foods.saveAndFlush(product);
        service.start("admin@test");
        service.step();
        var second = items.findById(product.getId()).orElseThrow();
        assertNotEquals(hash, second.getInputFingerprint());
        assertFalse(second.getFindingsJson().contains("USDA_CLASSIFICATION_CONFLICT"));
        assertFalse(second.getFindingsJson().contains("CONCEPT_CANONICAL_KEY_DRIFT"));
    }
    @Test
    void exactReviewedNamedManufacturerIsNotMisclassifiedAsGenericByAudit() {
        var product = food("Kefir, lowfat, plain, LIFEWAY");
        product.setSourceKey("USDA_FOODDATA:fdc:170904");
        product.setSourceDataType("SR Legacy");
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        foods.saveAndFlush(product);
        service.start("admin@test");
        service.step();
        assertFalse(items.findById(product.getId()).orElseThrow().getFindingsJson()
                .contains("USDA_CLASSIFICATION_CONFLICT"));
        assertEquals(FoodCatalogType.BRANDED_PRODUCT, foods.findById(product.getId()).orElseThrow().getCatalogType());
    }

    @Test
    void openSuggestionsAndPreviouslyValidatedRowsDoNotStarveLaterProductsAfterRestart() {
        var first = food("First");
        first.setQualityValidatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        foods.saveAndFlush(first);
        var second = food("Second");
        food("Third");
        var last = food("Fourth");
        var open = new ProductQualitySuggestionEntity();
        open.setFoodItem(first);
        open.setSource(ProductQualitySuggestionSource.RULE_BASED);
        open.setSuggestionType(ProductQualitySuggestionType.NAME_CLEANUP);
        open.setStatus(ProductQualitySuggestionStatus.OPEN);
        open.setSuggestedValue("First revised");
        suggestionRepository.saveAndFlush(open);

        var start = service.start("admin@test");
        var batch = service.step();
        assertEquals(second.getId(), batch.cursorId());
        assertEquals(1, batch.reviewRequired());
        assertEquals("RUNNING", batch.status());
        assertEquals(start.scanRunId(), service.start("another-admin@test").scanRunId());
        var completed = newWorker().step();
        assertEquals(last.getId(), completed.cursorId());
        assertEquals("COMPLETED", completed.status());
        assertEquals(4, items.count());
        assertEquals(4, runs.findById(start.scanRunId()).orElseThrow().getScannedProducts());
        assertEquals(0, runs.findById(start.scanRunId()).orElseThrow().getValidatedProducts());
        assertEquals("First", foods.findById(first.getId()).orElseThrow().getName());
        assertEquals(first.getQualityValidatedAt(), foods.findById(first.getId()).orElseThrow().getQualityValidatedAt());
        assertEquals(1, suggestionRepository.count());
    }

    @Test
    void failedProductRollsBackRetriesWithBackoffAndEndsInDeadLetterWithoutBlockingHealthyRows() {
        var broken = food("Broken");
        food("Healthy1");
        var last = food("Healthy2");
        doAnswer(invocation -> {
            FoodItemEntity product = invocation.getArgument(0);
            if (product.getId().equals(broken.getId())) {
                product.setName("Must roll back");
                throw new IllegalStateException("secret-token must never be stored");
            }
            return 0;
        }).when(suggestions).createRuleSuggestionsForAudit(any());
        service.start("admin@test");
        var first = service.step();
        assertEquals(1, first.retriesPending());
        assertEquals("Broken", foods.findById(broken.getId()).orElseThrow().getName());
        assertEquals("IllegalStateException", items.findById(broken.getId()).orElseThrow().getLastErrorCode());
        assertFalse(items.findById(broken.getId()).orElseThrow().getLastErrorCode().contains("secret"));
        at(service, "2026-10-11T11:02:00Z");
        var second = service.step();
        assertEquals(last.getId(), second.cursorId());
        assertEquals("RUNNING", second.status());
        assertEquals(LocalDateTime.of(2026, 10, 11, 11, 6), items.findById(broken.getId()).orElseThrow().getNextAttemptAt());
        service.step();
        assertEquals(2, items.findById(broken.getId()).orElseThrow().getAttempts());
        at(service, "2026-10-11T11:06:00Z");
        var failed = service.step();
        assertEquals("FAILED", failed.status());
        assertEquals(1, failed.deadLetters());
        assertEquals(0, failed.retriesPending());
        assertEquals(2, failed.rulesPassed());
        assertEquals(3, runs.findById(failed.scanRunId()).orElseThrow().getScannedProducts());
        assertEquals("Broken", foods.findById(broken.getId()).orElseThrow().getName());
        assertFalse(failed.leased());
    }

    @Test
    void activeLeaseRejectsSecondWorkerAndExpiredLeaseCanBeRecovered() {
        food("One");
        service.start("admin@test");
        var state = states.findById(1L).orElseThrow();
        state.setLeaseOwner("interrupted-worker");
        state.setLeaseUntil(LocalDateTime.of(2026, 10, 11, 11, 1));
        states.saveAndFlush(state);
        assertTrue(service.step().leased());
        assertEquals(0, items.count());
        at(service, "2026-10-11T11:02:00Z");
        assertEquals("COMPLETED", service.step().status());
        assertFalse(service.status().leased());
        assertEquals(1, items.count());
    }

    @Test
    void frozenHighWaterAndCancellationDoNotLoseLaterImports() {
        food("One");
        var initial = service.start("admin@test");
        var later = food("Later import");
        assertEquals("COMPLETED", service.step().status());
        assertEquals(initial.highWaterId(), service.status().cursorId());
        assertTrue(items.findById(later.getId()).isEmpty());
        var next = service.start("admin@test");
        assertNotEquals(initial.scanRunId(), next.scanRunId());
        assertEquals(later.getId(), next.highWaterId());
        assertEquals("CANCELLED", service.cancel().status());
        service.step();
        assertTrue(items.findById(later.getId()).isEmpty());
        service.start("admin@test");
        assertEquals("COMPLETED", service.step().status());
        assertTrue(items.findById(later.getId()).isPresent());
    }

    @Test
    void changedRulesetCannotResumeSilently() {
        food("One");
        service.start("admin@test");
        var state = states.findById(1L).orElseThrow();
        state.setRulesetVersion("old-rules");
        states.saveAndFlush(state);
        assertThrows(IllegalArgumentException.class, service::step);
        assertEquals(0, items.count());
        assertFalse(service.status().leased());
        service.cancel();
        assertEquals(ProductCatalogAuditService.RULESET, service.start("admin@test").rulesetVersion());
    }

    @Test
    void wallTimeBudgetCheckpointsCompletedProductsAndReleasesLease() {
        var first = food("One");
        food("Two");
        properties.setTimeBudget(Duration.ofSeconds(1));
        when(suggestions.createRuleSuggestionsForAudit(any())).thenAnswer(invocation -> { Thread.sleep(1100); return 0; });
        service.start("admin@test");
        var status = service.step();
        assertEquals(first.getId(), status.cursorId());
        assertEquals("RUNNING", status.status());
        assertFalse(status.leased());
        assertEquals(1, items.count());
    }

    @Test
    void startupCatchupIsOptInAndHonorsIrishDowntimeWithDst() {
        food("One");
        service.scheduledStep();
        assertEquals("READY", service.status().status());
        properties.setEnabled(true);
        at(service, "2026-10-11T00:30:00Z"); // 01:30 Dublin, inclusive downtime start.
        assertTrue(service.inDowntime());
        service.scheduledStep();
        assertEquals("READY", service.status().status());
        at(service, "2026-10-11T04:00:00Z"); // 05:00 Dublin, exclusive downtime end.
        assertFalse(service.inDowntime());
        service.scheduledStep();
        assertEquals("COMPLETED", service.status().status());
        Long run = service.status().scanRunId();
        service.scheduledStep();
        assertEquals(run, service.status().scanRunId());
        at(service, "2026-10-12T04:00:00Z");
        service.scheduledStep();
        assertNotEquals(run, service.status().scanRunId());
    }

    @Test
    void boundedPagesDoNotSkipRowsInLargerSweepAndNewPassDoesNotAccumulateItemHistory() {
        properties.setBatchSize(7);
        for (int index = 0; index < 30; index++) food("Batch " + index);
        var initial = service.start("admin@test");
        for (int step = 0; step < 4; step++) {
            assertEquals("RUNNING", newWorker().step().status());
            assertEquals((step + 1) * 7, items.count());
        }
        assertEquals("COMPLETED", newWorker().step().status());
        assertEquals(30, items.count());
        assertEquals(30, runs.findById(initial.scanRunId()).orElseThrow().getScannedProducts());
        assertEquals(30, service.items(null, -1, 2000).getContent().size());
        assertEquals(100, service.items(null, -1, 2000).getSize());
        assertThrows(IllegalArgumentException.class, () -> service.items("INVALID", 0, 50));
        var next = service.start("admin@test");
        while ("RUNNING".equals(service.status().status())) service.step();
        assertEquals(30, items.count());
        assertEquals(30, service.status().rulesPassed());
        assertEquals(30, runs.findById(next.scanRunId()).orElseThrow().getScannedProducts());
    }

    @Test
    void inputFingerprintIsStableAcrossPassesAndChangesWithSourceData() {
        var product = food("Fingerprint");
        service.start("admin@test");
        service.step();
        String original = items.findById(product.getId()).orElseThrow().getInputFingerprint();
        service.start("admin@test");
        service.step();
        assertEquals(original, items.findById(product.getId()).orElseThrow().getInputFingerprint());
        product.setLastExternalSyncAt(LocalDateTime.of(2026, 10, 11, 12, 0));
        foods.saveAndFlush(product);
        service.start("admin@test");
        service.step();
        assertNotEquals(original, items.findById(product.getId()).orElseThrow().getInputFingerprint());
    }

    @Test
    void winterAndCrossMidnightDowntimeUseLocalClock() {
        at(service, "2026-12-01T01:30:00Z");
        assertTrue(service.inDowntime());
        at(service, "2026-12-01T05:00:00Z");
        assertFalse(service.inDowntime());
        properties.setDowntimeStart(LocalTime.of(23, 0));
        properties.setDowntimeEnd(LocalTime.of(2, 0));
        at(service, "2026-12-01T00:00:00Z");
        assertTrue(service.inDowntime());
        at(service, "2026-12-01T12:00:00Z");
        assertFalse(service.inDowntime());
    }

    @Test
    void verifiedVolumeServingDoesNotRequireFakeGramWeight() {
        var product = food("Milk volume");
        product.setServingSizeGrams(null);
        product.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100ML);
        foods.saveAndFlush(product);
        var option = new FoodItemServingOptionEntity();
        option.setFoodItem(product);
        option.setLabel("Glass");
        option.setUnitType(FoodServingOptionUnit.CUP);
        option.setQuantity(1.0);
        option.setMlVolume(200.0);
        option.setIsDefault(true);
        option.setUserVisible(true);
        option.setSource(FoodServingOptionSource.ADMIN);
        option.setQualityStatus(FoodServingOptionQualityStatus.VERIFIED);
        servings.saveAndFlush(option);
        service.start("admin@test");
        service.step();
        assertEquals("RULES_PASSED", items.findById(product.getId()).orElseThrow().getStatus());
        assertTrue(issues.findByFoodItemIdAndResolvedFalse(product.getId()).isEmpty());
        assertNull(foods.findById(product.getId()).orElseThrow().getServingSizeGrams());
        option.setQualityStatus(FoodServingOptionQualityStatus.NEEDS_REVIEW);
        servings.saveAndFlush(option);
        product.setServingSizeGrams(100.0); // A legacy gram weight does not establish a volume portion.
        foods.saveAndFlush(product);
        service.start("admin@test");
        service.step();
        assertTrue(items.findById(product.getId()).orElseThrow().getFindingsJson().contains("MISSING_SERVING_SIZE"));
    }

    @Test
    void beverageMassBasisEntersSourceReviewWithoutInventingMl() {
        var product = food("Milk");
        product.setSourceCategoryTags(new java.util.HashSet<>(java.util.Set.of("en:milks")));
        foods.saveAndFlush(product);
        service.start("reviewer");
        service.step();
        assertTrue(items.findById(product.getId()).orElseThrow().getFindingsJson().contains("LIQUID_MASS_BASIS_SOURCE_REVIEW"));
        assertEquals(FoodNutritionReferenceUnit.PER_100G, foods.findById(product.getId()).orElseThrow().getNutritionReferenceUnit());
    }

    @Test
    void partialNutritionAndVolumeBridgeAreFlaggedWithoutRelabellingOrPublishing() {
        var product = food("Milk");
        product.setProtein(null);
        product.setPublicationStatus(CatalogPublicationStatus.INTERNAL_REVIEW);
        foods.saveAndFlush(product);
        var option = new FoodItemServingOptionEntity();
        option.setFoodItem(product);
        option.setLabel("Cup");
        option.setUnitType(FoodServingOptionUnit.CUP);
        option.setQuantity(1.0);
        option.setMlVolume(250.0);
        option.setIsDefault(true);
        option.setSource(FoodServingOptionSource.ADMIN);
        option.setQualityStatus(FoodServingOptionQualityStatus.VERIFIED);
        servings.saveAndFlush(option);
        service.start("admin@test");
        service.step();
        var report = items.findById(product.getId()).orElseThrow();
        assertEquals("REVIEW_REQUIRED", report.getStatus());
        assertTrue(report.getFindingsJson().contains("MISSING_MACROS"));
        assertTrue(report.getFindingsJson().contains("VOLUME_WITHOUT_MASS_BRIDGE"));
        assertEquals(64, report.getInputFingerprint().length());
        var persisted = foods.findById(product.getId()).orElseThrow();
        assertNull(persisted.getProtein());
        assertEquals(FoodNutritionReferenceUnit.PER_100G, persisted.getNutritionReferenceUnit());
        assertEquals(CatalogPublicationStatus.INTERNAL_REVIEW, persisted.getPublicationStatus());
        assertEquals(1, issues.findByFoodItemIdAndResolvedFalse(product.getId()).size());
    }
}
