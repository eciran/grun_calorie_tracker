package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.impl.FoodProductReviewServiceImpl;
import com.grun.calorietracker.service.support.ExternalFoodImportFence;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogWriterFenceOrderingTest {
    private static final String ACTOR = "reviewer@example.invalid";
    private static final String REASON = "Reviewed source evidence";

    @TestFactory Stream<DynamicTest> reviewWritersFailBeforeAnyRecordRead() {
        var canonical = new FoodCanonicalResolutionRequestDto();
        canonical.setCanonicalFoodKey("banana:raw"); canonical.setPrimaryProductId(1L);
        var alias = new FoodSearchAliasRequestDto(); alias.setAlias("muz");
        return Stream.<NamedReview>of(
                new NamedReview("review", s -> s.updateProductReview(1L, new FoodProductReviewRequestDto(), ACTOR)),
                new NamedReview("alias-add", s -> s.addProductSearchAlias(1L, alias, ACTOR)),
                new NamedReview("alias-status", s -> s.updateProductSearchAliasStatus(1L, 2L, false, ACTOR)),
                new NamedReview("barcode", s -> s.updateProductBarcode(1L,
                        new FoodProductBarcodeUpdateRequestDto("3017620422003", REASON), ACTOR)),
                new NamedReview("create", s -> s.createAdminCatalogProduct(createRequest(), ACTOR)),
                new NamedReview("canonical-set", s -> s.resolveCanonicalPrimary(canonical, ACTOR)),
                new NamedReview("canonical-clear", s -> s.clearCanonicalResolution("banana:raw", ACTOR)),
                new NamedReview("explicit-merge", s -> s.mergeDuplicateProducts(
                        new FoodProductMergeRequestDto(1L, List.of(2L)), ACTOR)),
                new NamedReview("correction-import", s -> s.importNutritionCorrections(corrections(), ACTOR, false, false))
        ).map(item -> DynamicTest.dynamicTest(item.name(), () -> {
            var f = new ReviewFixture();
            assertSame(f.denied, assertThrows(RuntimeException.class, () -> item.action().accept(f.service)));
            verify(f.fence).acquire();
            verifyNoInteractions(f.products, f.logs, f.favorites, f.audits, f.issues, f.aliases, f.canonical, f.tracker);
        }));
    }

    @TestFactory Stream<DynamicTest> brandedWritersFailBeforeFingerprintOrDecisionRead() {
        return Stream.of("decide", "clear", "apply", "revert").map(operation -> DynamicTest.dynamicTest(operation, () -> {
            var fence = mock(ExternalFoodImportFence.class);
            var denied = new IllegalStateException("fence denied before read");
            doThrow(denied).when(fence).acquire();
            var analysis = mock(BrandedProductDuplicateAnalysisService.class);
            var decisions = mock(FoodBrandedDuplicateDecisionRepository.class);
            var decisionAudits = mock(FoodBrandedDuplicateDecisionAuditRepository.class);
            var products = mock(FoodItemRepository.class);
            var collapses = mock(FoodBrandedDuplicateSearchCollapseRepository.class);
            var members = mock(FoodBrandedDuplicateSearchCollapseMemberRepository.class);
            var audits = mock(FoodBrandedDuplicateSearchCollapseAuditRepository.class);
            var decisionService = new BrandedProductDuplicateDecisionService(analysis, decisions, decisionAudits, products, collapses);
            var collapseService = new BrandedDuplicateSearchCollapseService(analysis, decisions, collapses, members, audits, products);
            installIfPresent(decisionService, fence); installIfPresent(collapseService, fence);
            assertSame(denied, assertThrows(RuntimeException.class, () -> {
                switch (operation) {
                    case "decide" -> decisionService.decide(new BrandedProductDuplicateDecisionRequestDto(
                            "brand", "name", BrandedDuplicateDecision.SURVIVOR_SELECTED, 1L, REASON, "a".repeat(64)), ACTOR);
                    case "clear" -> decisionService.clear("brand", "name", REASON, ACTOR);
                    case "apply" -> collapseService.apply(new BrandedDuplicateSearchCollapseRequestDto(
                            "brand", "name", "a".repeat(64), REASON), ACTOR);
                    case "revert" -> collapseService.revert("brand", "name", REASON, ACTOR);
                    default -> throw new AssertionError(operation);
                }
            }));
            verify(fence).acquire();
            verifyNoInteractions(analysis, decisions, decisionAudits, products, collapses, members, audits);
        }));
    }

    @Test void readOnlyPreflightDoesNotAcquireWriterFence() {
        var f = new ReviewFixture();
        assertTrue(f.service.preflightAdminCatalogProduct(createRequest()).getDuplicateCandidates().isEmpty());
        verifyNoInteractions(f.fence);
    }

    @Test void correctionDryRunDoesNotAcquireWriterFenceOrWrite() {
        var f = new ReviewFixture();
        var result = f.service.importNutritionCorrections(corrections(), ACTOR, true, false);
        assertEquals(1, result.getTotalRows()); assertEquals(0, result.getUpdatedRows());
        verifyNoInteractions(f.fence, f.audits, f.tracker);
        verify(f.products, never()).save(any());
    }

    @Test void correctionDatabaseFailureAbortsInsteadOfBecomingARowError() {
        var f = new ReviewFixture(); doNothing().when(f.fence).acquire();
        var failure = new org.springframework.dao.DataAccessResourceFailureException("database unavailable");
        when(f.products.findById(1L)).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class,
                () -> f.service.importNutritionCorrections(corrections(), ACTOR, false, false)));
        verifyNoInteractions(f.audits, f.tracker);
    }

    @Test void correctionMissingRecordRemainsARecoverableRowValidationError() {
        var f = new ReviewFixture(); doNothing().when(f.fence).acquire();
        var result = f.service.importNutritionCorrections(corrections(), ACTOR, false, false);
        assertEquals(1, result.getSkippedRows()); assertEquals(1, result.getErrors().size());
        verify(f.fence).acquire(); verifyNoInteractions(f.audits, f.tracker);
    }

    private record NamedReview(String name, Consumer<FoodProductReviewServiceImpl> action) {}

    private static MockMultipartFile corrections() {
        return new MockMultipartFile("file", "corrections.csv", "text/csv",
                "id,calories\n1,89\n".getBytes(StandardCharsets.UTF_8));
    }

    private static AdminFoodProductCreateRequestDto createRequest() {
        var request = new AdminFoodProductCreateRequestDto();
        request.setName("Banana"); request.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        request.setMarketRegion(MarketRegion.UK_IE); request.setPreparationState(FoodPreparationState.RAW);
        request.setCalories(89.0); request.setProtein(1.1); request.setFat(0.3); request.setCarbs(22.8);
        request.setSourceName("Test fixture"); request.setSourceUrl("https://example.invalid/source");
        return request;
    }

    // Allows the same regression to fail on pre-fix code because reads precede the missing fence.
    private static void installIfPresent(Object service, ExternalFoodImportFence fence) {
        try {
            var field = service.getClass().getDeclaredField("externalFoodImportFence");
            field.setAccessible(true); field.set(service, fence);
        } catch (NoSuchFieldException ignored) {
            // Baseline has no participation; the assertion must report actual premature repository reads.
        } catch (IllegalAccessException ex) { throw new AssertionError(ex); }
    }

    private static class ReviewFixture {
        final FoodItemRepository products = mock(FoodItemRepository.class);
        final com.grun.calorietracker.repository.FoodItemLocalizationRepository localizations = mock(com.grun.calorietracker.repository.FoodItemLocalizationRepository.class);
        final FoodLogsRepository logs = mock(FoodLogsRepository.class);
        final UserFavoriteRepository favorites = mock(UserFavoriteRepository.class);
        final FoodProductReviewAuditRepository audits = mock(FoodProductReviewAuditRepository.class);
        final FoodProductQualityIssueRepository issues = mock(FoodProductQualityIssueRepository.class);
        final FoodItemSearchAliasRepository aliases = mock(FoodItemSearchAliasRepository.class);
        final FoodCanonicalResolutionRepository canonical = mock(FoodCanonicalResolutionRepository.class);
        final FoodProductQualityIssueTracker tracker = mock(FoodProductQualityIssueTracker.class);
        final ExternalFoodImportFence fence = mock(ExternalFoodImportFence.class);
        final RuntimeException denied = new IllegalStateException("fence denied before read");
        final FoodProductReviewServiceImpl service = new FoodProductReviewServiceImpl(
                products, localizations, logs, favorites, audits, issues, aliases, canonical, tracker);
        ReviewFixture() { doThrow(denied).when(fence).acquire(); installIfPresent(service, fence); }
    }
}
