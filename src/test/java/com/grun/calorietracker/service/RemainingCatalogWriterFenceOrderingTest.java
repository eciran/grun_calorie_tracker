package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.ProductIntakeApplyField;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.service.model.FoodProductReviewCaseCommand;
import com.grun.calorietracker.service.impl.*;
import com.grun.calorietracker.service.support.*;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemainingCatalogWriterFenceOrderingTest {
    private record Scenario(String name, Class<?> type, Consumer<Object> action) {}

    @TestFactory Stream<DynamicTest> lockDenialPrecedesRepositoryOrRowLockAccess() {
        String actor = "reviewer@example.invalid";
        return Stream.of(
            new Scenario("serving-create", AdminFoodServingOptionServiceImpl.class,
                s -> ((AdminFoodServingOptionServiceImpl)s).create(1L, new AdminFoodServingOptionUpsertRequestDto(), actor)),
            new Scenario("serving-update", AdminFoodServingOptionServiceImpl.class,
                s -> ((AdminFoodServingOptionServiceImpl)s).update(1L, 2L, new AdminFoodServingOptionUpsertRequestDto(), actor)),
            new Scenario("publish", CatalogPublicationServiceImpl.class,
                s -> ((CatalogPublicationServiceImpl)s).publish(1L, actor, "Source reviewed", null)),
            new Scenario("publish-new", CatalogPublicationServiceImpl.class,
                s -> ((CatalogPublicationServiceImpl)s).publishNew(new FoodItemEntity(), actor, "Source reviewed", null)),
            new Scenario("intake-publish", AdminProductIntakeServiceImpl.class,
                s -> ((AdminProductIntakeServiceImpl)s).publishCandidate(1L, actor, "Source reviewed", null, true)),
            new Scenario("intake-apply", AdminProductIntakeServiceImpl.class,
                s -> ((AdminProductIntakeServiceImpl)s).applyExistingProduct(1L, actor, Set.of(ProductIntakeApplyField.PRODUCT_NAME), true)),
            new Scenario("intake-attach-existing", AdminProductIntakeServiceImpl.class,
                s -> ((AdminProductIntakeServiceImpl)s).attachExistingProduct(1L, actor, 2L)),
            new Scenario("review-case-finalize", FoodProductReviewCaseServiceImpl.class,
                s -> ((FoodProductReviewCaseServiceImpl)s).finalizeCase(reviewCaseCommand())),
            new Scenario("rule-suggestion-scan", ProductQualitySuggestionServiceImpl.class,
                s -> ((ProductQualitySuggestionServiceImpl)s).scanSuggestions(
                        MarketRegion.UK_IE, 25, false, ProductQualityScanTriggerType.MANUAL, actor)),
            new Scenario("stale-source-queue", FoodCatalogMaintenanceServiceImpl.class,
                s -> ((FoodCatalogMaintenanceServiceImpl)s).refreshStaleSourceQueue()),
            new Scenario("food-review-assignment", AdminCatalogOperationsServiceImpl.class,
                s -> ((AdminCatalogOperationsServiceImpl)s).assignReview(actor, "FOOD", 1L,
                        new CatalogReviewAssignmentRequestDto(actor,
                                java.time.LocalDateTime.now().plusDays(1), "Capacity review"))),
            new Scenario("source-evidence-append", FoodProductEvidenceServiceImpl.class,
                s -> ((FoodProductEvidenceServiceImpl)s).recordImportEvidence(
                        List.of(evidenceProduct()), FoodEvidenceBasis.PER_100_G,
                        java.time.LocalDateTime.now(), "writer-fence-v1", actor)),
            new Scenario("review-evidence-resubmit", FoodProductReviewSubmissionServiceImpl.class,
                s -> ((FoodProductReviewSubmissionServiceImpl)s).resubmitEvidence(actor, 1L, "session-1")),
            new Scenario("suggestion-accept", ProductQualitySuggestionServiceImpl.class,
                s -> ((ProductQualitySuggestionServiceImpl)s).acceptSuggestion(1L, actor)),
            new Scenario("suggestion-reject", ProductQualitySuggestionServiceImpl.class,
                s -> ((ProductQualitySuggestionServiceImpl)s).rejectSuggestion(1L, actor)),
            new Scenario("typed-append", TypedNutrientEvidenceService.class,
                s -> ((TypedNutrientEvidenceService)s).append(1L,
                    TypedNutrientEvidenceServiceTest.request(TypedNutrientEvidenceServiceTest.BODY, "a".repeat(64)), actor)),
            new Scenario("observation-append", TypedNutrientEvidenceService.class,
                s -> ((TypedNutrientEvidenceService)s).appendObservation(1L,
                    new TypedSourceObservationRequest(TypedSourceObservationContract.VERSION, "USDA_FOODDATA", "vitamin_e",
                        TypedSourceObservationContractTest.USDA, TypedNutrientEvidenceServiceTest.hash(TypedSourceObservationContractTest.USDA),
                        Instant.now().minusSeconds(60), "a".repeat(64), true, "Explicit source-only test review"), actor))
        ).map(scenario -> DynamicTest.dynamicTest(scenario.name(), () -> {
            var mocks = new ArrayList<Object>();
            var constructor = scenario.type().getConstructors()[0];
            var args = Arrays.stream(constructor.getParameterTypes()).map(type -> {
                if (type == ObjectMapper.class) return (Object)new ObjectMapper();
                if (type == boolean.class) return Boolean.TRUE;
                if (type == long.class) return 24L;
                if (type == List.class) return List.of();
                var value = mock(type); mocks.add(value); return value;
            }).toArray();
            Object service = constructor.newInstance(args);
            var fence = mock(ExternalFoodImportFence.class);
            var denied = new IllegalStateException("catalog fence denied before inventory");
            doThrow(denied).when(fence).acquire();
            try {
                var field = scenario.type().getDeclaredField("externalFoodImportFence");
                field.setAccessible(true); field.set(service, fence);
            } catch (NoSuchFieldException ignored) {
                // Baseline proves actual premature reads instead of a missing-field compilation failure.
            }
            assertSame(denied, assertThrows(RuntimeException.class, () -> scenario.action().accept(service)));
            verify(fence).acquire(); verifyNoInteractions(mocks.toArray());
        }));
    }

    private static FoodProductReviewCaseCommand reviewCaseCommand() {
        return new FoodProductReviewCaseCommand(
                "writer-fence-idempotency", FoodProductReviewCaseSource.ADMIN_MANUAL, null, null,
                "3017620422003", MarketRegion.UK_IE, null, "Hazelnut spread", "Example",
                539.0, 6.3, 30.9, 57.5, null, null, null,
                FoodNutritionBasis.SOURCE_REPORTED, FoodProductReviewRiskLevel.MEDIUM, 1,
                "{\"productName\":\"Hazelnut spread\"}", null, null, "v1",
                false, false, false, null
        );
    }

    private static FoodItemEntity evidenceProduct() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(1L);
        product.setDataSource(FoodDataSource.USDA_FOODDATA);
        product.setSourceKey("USDA_FOODDATA:fdc:1");
        product.setCalories(100.0);
        return product;
    }
}
