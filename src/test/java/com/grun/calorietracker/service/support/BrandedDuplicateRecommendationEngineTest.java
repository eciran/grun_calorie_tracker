package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.BrandedProductDuplicateCandidateDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BrandedDuplicateRecommendationEngineTest {

    @Test
    void recommendsSearchCollapseForEquivalentRecords() {
        var result = BrandedDuplicateRecommendationEngine.classify(List.of(
                candidate(1L, 330d, 95), candidate(2L, 330d, 80)
        ));
        assertThat(result.action()).isEqualTo("COLLAPSE_DUPLICATES");
        assertThat(result.suggestedSurvivorProductId()).isEqualTo(1L);
        assertThat(result.confidence()).isEqualTo(99);
    }

    @Test
    void recommendsPackageFamilyWhenOnlyPackageQuantityDiffers() {
        var result = BrandedDuplicateRecommendationEngine.classify(List.of(
                candidate(1L, 330d, 95), candidate(2L, 500d, 80)
        ));
        assertThat(result.action()).isEqualTo("CREATE_PACKAGE_FAMILY");
        assertThat(result.confidence()).isEqualTo(96);
    }

    @Test
    void recommendsPackageFamilyWhenPackageNutritionScalesWithQuantity() {
        var first = new BrandedProductDuplicateCandidateDto(
                1L, "Cola", "111", "source:1", "GLOBAL", "UNSPECIFIED",
                330d, "MILLILITER", 139d, 0d, 35d, 0d, 95, "VERIFIED", "ADMIN"
        );
        var second = new BrandedProductDuplicateCandidateDto(
                2L, "Cola", "222", "source:2", "GLOBAL", "UNSPECIFIED",
                500d, "MILLILITER", 210.6d, 0d, 53d, 0d, 80, "VERIFIED", "ADMIN"
        );
        assertThat(BrandedDuplicateRecommendationEngine.classify(List.of(first, second)).action())
                .isEqualTo("CREATE_PACKAGE_FAMILY");
    }

    @Test
    void routesNutritionMismatchToManualReview() {
        var first = candidate(1L, 330d, 95);
        var second = new BrandedProductDuplicateCandidateDto(
                2L, "Cola", "222", "source:2", "GLOBAL", "UNSPECIFIED",
                500d, "MILLILITER", 40d, 0d, 10d, 0d, 80, "VERIFIED", "ADMIN"
        );
        var result = BrandedDuplicateRecommendationEngine.classify(List.of(first, second));
        assertThat(result.action()).isEqualTo("MANUAL_REVIEW");
        assertThat(result.blockers()).contains("NUTRITION_MISMATCH");
    }

    private BrandedProductDuplicateCandidateDto candidate(Long id, Double serving, int quality) {
        return new BrandedProductDuplicateCandidateDto(
                id, "Cola", String.valueOf(id), "source:" + id, "GLOBAL", "UNSPECIFIED",
                serving, "MILLILITER", 0d, 0d, 0d, 0d, quality, "VERIFIED", "ADMIN"
        );
    }
}
