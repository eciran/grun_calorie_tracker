package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.ImageStatus;
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
        if (hasText(product.getDisplayImageUrl()) && product.getImageStatus() == ImageStatus.APPROVED) {
            score += 15;
        } else if (hasText(product.getExternalImageUrl())) {
            score += 5;
        }
        return Math.min(score, 100);
    }

    private static int calculateReviewPriority(FoodItemEntity product) {
        long usageCount = product.getUsageCount() == null ? 0L : product.getUsageCount();
        int priority = (int) Math.min(usageCount * 10, 500);
        if (product.getVerificationStatus() == VerificationStatus.RAW_IMPORTED) {
            priority += 80;
        }
        if (product.getImageStatus() == ImageStatus.NEEDS_REVIEW) {
            priority += 40;
        }
        if (!hasText(product.getDisplayImageUrl())) {
            priority += 20;
        }
        if (product.getQualityScore() != null && product.getQualityScore() < 60) {
            priority += 30;
        }
        return priority;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
