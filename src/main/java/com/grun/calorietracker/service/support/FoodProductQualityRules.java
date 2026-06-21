package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.VerificationStatus;

import java.time.LocalDateTime;

public final class FoodProductQualityRules {

    private FoodProductQualityRules() {
    }

    public static void markExternalImport(FoodItemEntity product) {
        if (product.getUsageCount() == null) {
            product.setUsageCount(0L);
        }
        product.setLastExternalSyncAt(LocalDateTime.now());
        updateQualityAndReviewPriority(product);
    }

    public static void markUsed(FoodItemEntity product) {
        long currentUsageCount = product.getUsageCount() == null ? 0L : product.getUsageCount();
        product.setUsageCount(currentUsageCount + 1);
        updateQualityAndReviewPriority(product);
    }

    public static void markReviewed(FoodItemEntity product) {
        product.setLastReviewedAt(LocalDateTime.now());
        updateQualityAndReviewPriority(product);
    }

    public static void updateQualityAndReviewPriority(FoodItemEntity product) {
        product.setQualityScore(calculateQualityScore(product));
        product.setConfidenceScore(calculateConfidenceScore(product));
        product.setAutoApprovedForCatalog(isAutoApprovedForCatalog(product));
        product.setReviewPriority(calculateReviewPriority(product));
    }

    private static int calculateQualityScore(FoodItemEntity product) {
        int score = 0;
        if (product.getVerificationStatus() == VerificationStatus.VERIFIED) {
            score += 45;
        } else if (product.getVerificationStatus() == VerificationStatus.NEEDS_REVIEW) {
            score += 20;
        } else if (product.getVerificationStatus() == VerificationStatus.RAW_IMPORTED) {
            score += 10;
        }
        if (hasText(product.getName())) {
            score += 10;
        }
        if (hasText(product.getBarcode())) {
            score += 10;
        }
        if (product.getCalories() != null) {
            score += 10;
        }
        if (product.getProtein() != null || product.getFat() != null || product.getCarbs() != null) {
            score += 10;
        }
        if (product.getServingSizeGrams() != null) {
            score += 5;
        }
        if (product.getMarketRegion() != null) {
            score += 5;
        }
        return Math.min(score, 100);
    }

    private static int calculateReviewPriority(FoodItemEntity product) {
        long usageCount = product.getUsageCount() == null ? 0L : product.getUsageCount();
        int priority = (int) Math.min(usageCount * 10, 500);
        if (product.getVerificationStatus() == VerificationStatus.RAW_IMPORTED
                && !Boolean.TRUE.equals(product.getAutoApprovedForCatalog())) {
            priority += 80;
        }
        if (product.getConfidenceScore() != null && product.getConfidenceScore() < 70) {
            priority += 30;
        }
        return priority;
    }

    public static boolean isAutoApprovedForCatalog(FoodItemEntity product) {
        if (product.getVerificationStatus() == VerificationStatus.VERIFIED) {
            return true;
        }
        if (product.getVerificationStatus() == VerificationStatus.REJECTED
                || product.getVerificationStatus() == VerificationStatus.NEEDS_REVIEW) {
            return false;
        }
        return calculateConfidenceScore(product) >= 80 && !hasCriticalIssue(product);
    }

    public static int calculateConfidenceScore(FoodItemEntity product) {
        int score = 0;
        if (hasText(product.getName())) {
            score += 15;
        }
        if (hasText(product.getBarcode()) || hasText(product.getNormalizedBarcode()) || hasText(product.getSourceKey())) {
            score += 15;
        }
        if (hasText(product.getBrand())) {
            score += 10;
        }
        if (product.getCalories() != null) {
            score += 15;
        }
        if (product.getProtein() != null && product.getFat() != null && product.getCarbs() != null) {
            score += 20;
        } else if (product.getProtein() != null || product.getFat() != null || product.getCarbs() != null) {
            score += 10;
        }
        if (product.getServingSizeGrams() != null) {
            score += 10;
        }
        if (product.getMarketRegion() != null) {
            score += 10;
        }
        if (product.getDataSource() != null) {
            score += 5;
        }
        if (hasCriticalIssue(product)) {
            score -= 35;
        }
        return Math.max(0, Math.min(score, 100));
    }

    public static boolean hasCriticalIssue(FoodItemEntity product) {
        return product == null
                || product.getCalories() == null
                || (product.getProtein() == null && product.getFat() == null && product.getCarbs() == null)
                || isGreaterThan(product.getCalories(), 1000.0)
                || isGreaterThan(product.getProtein(), 100.0)
                || isGreaterThan(product.getFat(), 100.0)
                || isGreaterThan(product.getCarbs(), 100.0)
                || isNegative(product.getFiber())
                || isNegative(product.getSugar())
                || isNegative(product.getSodium())
                || isGreaterThan(product.getSugar(), product.getCarbs())
                || isGreaterThan(product.getSaturatedFat(), product.getFat())
                || isGreaterThan(product.getTransFat(), product.getFat())
                || isGreaterThan(product.getSodium(), 10.0);
    }

    private static boolean isNegative(Double value) {
        return value != null && value < 0;
    }

    private static boolean isGreaterThan(Double value, Double limit) {
        return value != null && limit != null && value > limit;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
