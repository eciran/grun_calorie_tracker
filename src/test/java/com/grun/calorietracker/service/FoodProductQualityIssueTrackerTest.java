package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductQualityIssueEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.FoodProductQualityIssue;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodProductQualityIssueRepository;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import com.grun.calorietracker.service.support.FoodProductQualityRules;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoodProductQualityIssueTrackerTest {

    private final FoodProductQualityIssueRepository repository = mock(FoodProductQualityIssueRepository.class);
    private final FoodProductQualityIssueTracker tracker = new FoodProductQualityIssueTracker(repository);

    @Test
    void syncImportIssues_createsCurrentIssuesAndResolvesFixedOnes() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(10L);
        product.setName("Imported Product");
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        product.setNormalizedBarcode("NOTNUMERIC");
        product.setQualityScore(45);
        product.setMarketRegion(MarketRegion.GLOBAL);

        FoodProductQualityIssueEntity fixedIssue = new FoodProductQualityIssueEntity();
        fixedIssue.setFoodItem(product);
        fixedIssue.setIssueType(FoodProductQualityIssue.MISSING_REGION);
        fixedIssue.setResolved(false);

        when(repository.findByFoodItemIdAndResolvedFalse(eq(10L))).thenReturn(List.of(fixedIssue));

        tracker.syncImportIssues(product, false, true, "admin@grun.app");

        ArgumentCaptor<List<FoodProductQualityIssueEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        List<FoodProductQualityIssueEntity> savedIssues = captor.getValue();
        assertTrue(savedIssues.stream().anyMatch(issue -> issue.getIssueType() == FoodProductQualityIssue.LOW_QUALITY));
        assertTrue(savedIssues.stream().anyMatch(issue -> issue.getIssueType() == FoodProductQualityIssue.INVALID_BARCODE_FORMAT));
        assertTrue(savedIssues.stream().anyMatch(issue -> issue.getIssueType() == FoodProductQualityIssue.UNSUPPORTED_REGION));

        FoodProductQualityIssueEntity resolvedMissingRegion = savedIssues.stream()
                .filter(issue -> issue.getIssueType() == FoodProductQualityIssue.MISSING_REGION)
                .findFirst()
                .orElseThrow();
        assertTrue(resolvedMissingRegion.getResolved());
        assertEquals("admin@grun.app", resolvedMissingRegion.getResolvedBy());
        assertNotNull(resolvedMissingRegion.getResolvedAt());

        FoodProductQualityIssueEntity invalidBarcode = savedIssues.stream()
                .filter(issue -> issue.getIssueType() == FoodProductQualityIssue.INVALID_BARCODE_FORMAT)
                .findFirst()
                .orElseThrow();
        assertFalse(invalidBarcode.getResolved());
        assertTrue(invalidBarcode.getReason().contains("between 6 and 18 digits"));
    }

    @Test
    void syncReviewIssues_flagsSuspiciousNutritionValues() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(11L);
        product.setName("Suspicious Product");
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        product.setNormalizedBarcode("3017620422003");
        product.setMarketRegion(MarketRegion.TR);
        product.setCalories(1200.0);
        product.setProtein(130.0);
        product.setFat(12.0);
        product.setCarbs(30.0);
        product.setServingSizeGrams(100.0);
        product.setImageUrl("https://cdn.grun.app/products/3017620422003.jpg");

        when(repository.findByFoodItemIdAndResolvedFalse(eq(11L))).thenReturn(List.of());

        tracker.syncReviewIssues(product, "admin@grun.app");

        ArgumentCaptor<List<FoodProductQualityIssueEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        List<FoodProductQualityIssueEntity> savedIssues = captor.getValue();
        assertTrue(savedIssues.stream().anyMatch(issue -> issue.getIssueType() == FoodProductQualityIssue.SUSPICIOUS_CALORIES));
        assertTrue(savedIssues.stream().anyMatch(issue -> issue.getIssueType() == FoodProductQualityIssue.SUSPICIOUS_MACROS));
    }

    @Test
    void deriveFieldIssues_whenRawExternalProductHasCoreData_doesNotFlagLowQuality() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(12L);
        product.setName("Coke Zero");
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        product.setBarcode("5449000214799");
        product.setNormalizedBarcode("5449000214799");
        product.setMarketRegion(MarketRegion.UK_IE);
        product.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        product.setImageStatus(ImageStatus.NEEDS_REVIEW);
        product.setExternalImageUrl("https://images.openfoodfacts.org/images/products/544/900/021/4799/front_en.363.400.jpg");
        product.setCalories(1.0);
        product.setProtein(0.0);
        product.setFat(0.0);
        product.setCarbs(0.0);
        product.setServingSizeGrams(100.0);

        FoodProductQualityRules.markExternalImport(product);

        assertTrue(product.getQualityScore() >= 60);
        assertFalse(tracker.deriveFieldIssues(product).containsKey(FoodProductQualityIssue.LOW_QUALITY));
    }

    @Test
    void deriveFieldIssues_whenMicronutrientsAndNutrientQualityFieldsAreMissing_flagsReviewNeeds() {
        FoodItemEntity product = new FoodItemEntity();
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        product.setName("Incomplete Product");
        product.setNormalizedBarcode("1234567890123");
        product.setMarketRegion(MarketRegion.UK_IE);
        product.setCalories(100.0);
        product.setProtein(3.0);
        product.setFat(4.0);
        product.setCarbs(5.0);
        product.setServingSizeGrams(100.0);
        product.setImageUrl("https://cdn.grun.app/products/1234567890123.jpg");

        var issues = tracker.deriveFieldIssues(product);

        assertTrue(issues.containsKey(FoodProductQualityIssue.MISSING_MICRONUTRIENTS));
        assertTrue(issues.containsKey(FoodProductQualityIssue.MISSING_NUTRIENT_QUALITY_FIELDS));
    }

    @Test
    void deriveFieldIssues_whenNutrientQualityValuesAreImpossible_flagsSuspiciousQuality() {
        FoodItemEntity product = new FoodItemEntity();
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        product.setName("Suspicious Nutrient Product");
        product.setNormalizedBarcode("1234567890124");
        product.setMarketRegion(MarketRegion.UK_IE);
        product.setCalories(100.0);
        product.setProtein(3.0);
        product.setFat(4.0);
        product.setCarbs(5.0);
        product.setFiber(2.0);
        product.setSugar(8.0);
        product.setSodium(0.1);
        product.setSaturatedFat(6.0);
        product.setTransFat(0.0);
        product.setServingSizeGrams(100.0);
        product.setImageUrl("https://cdn.grun.app/products/1234567890124.jpg");

        var issues = tracker.deriveFieldIssues(product);

        assertTrue(issues.containsKey(FoodProductQualityIssue.SUSPICIOUS_NUTRIENT_QUALITY));
    }
}
