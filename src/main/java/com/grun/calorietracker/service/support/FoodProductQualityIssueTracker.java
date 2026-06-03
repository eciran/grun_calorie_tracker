package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductQualityIssueEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodProductQualityIssue;
import com.grun.calorietracker.repository.FoodProductQualityIssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FoodProductQualityIssueTracker {

    private final FoodProductQualityIssueRepository foodProductQualityIssueRepository;

    public void syncReviewIssues(FoodItemEntity product, String actor) {
        syncIssues(product, deriveFieldIssues(product), actor);
    }

    public void syncImportIssues(
            FoodItemEntity product,
            boolean missingRegion,
            boolean unsupportedRegion,
            String actor
    ) {
        Map<FoodProductQualityIssue, String> issues = deriveFieldIssues(product);
        if (missingRegion) {
            issues.put(FoodProductQualityIssue.MISSING_REGION, "Product has no explicit market region and was imported as GLOBAL.");
        }
        if (unsupportedRegion) {
            issues.put(FoodProductQualityIssue.UNSUPPORTED_REGION, "Product has an unsupported market region and was imported with fallback region.");
        }
        syncIssues(product, issues, actor);
    }

    public Map<FoodProductQualityIssue, String> deriveFieldIssues(FoodItemEntity product) {
        Map<FoodProductQualityIssue, String> issues = new EnumMap<>(FoodProductQualityIssue.class);
        if (product == null) {
            return issues;
        }
        if (product.getQualityScore() == null || product.getQualityScore() < 60) {
            issues.put(FoodProductQualityIssue.LOW_QUALITY, "Product quality score is below review threshold.");
        }
        if (isBlank(product.getImageUrl()) && isBlank(product.getExternalImageUrl()) && isBlank(product.getDisplayImageUrl())) {
            issues.put(FoodProductQualityIssue.MISSING_IMAGE, "Product has no image URL.");
        }
        if (product.getCalories() == null) {
            issues.put(FoodProductQualityIssue.MISSING_CALORIES, "Product has no calories value.");
        }
        if (product.getProtein() == null && product.getFat() == null && product.getCarbs() == null) {
            issues.put(FoodProductQualityIssue.MISSING_MACROS, "Product has no protein, fat, or carbohydrate values.");
        }
        if (product.getServingSizeGrams() == null) {
            issues.put(FoodProductQualityIssue.MISSING_SERVING_SIZE, "Product has no serving size value.");
        }
        if (product.getMarketRegion() == null) {
            issues.put(FoodProductQualityIssue.MISSING_REGION, "Product has no market region.");
        }
        if (requiresBarcode(product) && isBlank(product.getBarcode()) && isBlank(product.getNormalizedBarcode())) {
            issues.put(FoodProductQualityIssue.MISSING_BARCODE, "Branded product has no barcode.");
        }
        String normalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(product.getNormalizedBarcode());
        if (normalizedBarcode != null && !normalizedBarcode.matches("\\d{6,18}")) {
            issues.put(FoodProductQualityIssue.INVALID_BARCODE_FORMAT, "Product barcode is not a numeric value between 6 and 18 digits.");
        }
        return issues;
    }

    private void syncIssues(FoodItemEntity product, Map<FoodProductQualityIssue, String> currentIssues, String actor) {
        if (product == null) {
            return;
        }

        List<FoodProductQualityIssueEntity> activeIssues = product.getId() == null
                ? List.of()
                : foodProductQualityIssueRepository.findByFoodItemIdAndResolvedFalse(product.getId());
        Map<FoodProductQualityIssue, FoodProductQualityIssueEntity> activeByType = new LinkedHashMap<>();
        activeIssues.forEach(issue -> activeByType.put(issue.getIssueType(), issue));

        String resolvedActor = normalizeActor(actor);
        String identifier = resolveIdentifier(product);
        LocalDateTime now = LocalDateTime.now();
        List<FoodProductQualityIssueEntity> changes = new ArrayList<>();

        currentIssues.forEach((issueType, reason) -> {
            FoodProductQualityIssueEntity issue = activeByType.remove(issueType);
            if (issue == null) {
                issue = new FoodProductQualityIssueEntity();
                issue.setFoodItem(product);
                issue.setIssueType(issueType);
                issue.setFirstDetectedAt(now);
                issue.setResolved(false);
            }
            issue.setIdentifier(identifier);
            issue.setReason(reason);
            issue.setLastDetectedAt(now);
            issue.setResolved(false);
            issue.setResolvedAt(null);
            issue.setResolvedBy(null);
            changes.add(issue);
        });

        activeByType.values().forEach(issue -> {
            issue.setResolved(true);
            issue.setResolvedAt(now);
            issue.setResolvedBy(resolvedActor);
            changes.add(issue);
        });

        if (!changes.isEmpty()) {
            foodProductQualityIssueRepository.saveAll(changes);
        }
    }

    private boolean requiresBarcode(FoodItemEntity product) {
        return product.getCatalogType() == null || product.getCatalogType() == FoodCatalogType.BRANDED_PRODUCT;
    }

    private String resolveIdentifier(FoodItemEntity product) {
        if (!isBlank(product.getSourceKey())) {
            return product.getSourceKey().trim();
        }
        if (!isBlank(product.getNormalizedBarcode())) {
            return product.getNormalizedBarcode().trim();
        }
        if (!isBlank(product.getBarcode())) {
            return product.getBarcode().trim();
        }
        return isBlank(product.getName()) ? null : product.getName().trim();
    }

    private String normalizeActor(String actor) {
        return isBlank(actor) ? "system" : actor.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
