package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.BrandedDuplicateRecommendationDto;
import com.grun.calorietracker.dto.BrandedProductDuplicateCandidateDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class BrandedDuplicateRecommendationEngine {

    private static final double NUTRITION_TOLERANCE = 0.02d;

    private BrandedDuplicateRecommendationEngine() {
    }

    public static BrandedDuplicateRecommendationDto classify(
            List<BrandedProductDuplicateCandidateDto> candidates
    ) {
        if (candidates == null || candidates.size() < 2) {
            return manual("At least two products are required.", List.of("INSUFFICIENT_CANDIDATES"));
        }
        List<String> blockers = new ArrayList<>();
        if (distinct(candidates.stream().map(BrandedProductDuplicateCandidateDto::marketRegion).toList()) > 1) {
            blockers.add("MARKET_MISMATCH");
        }
        if (distinct(candidates.stream().map(BrandedProductDuplicateCandidateDto::preparationState).toList()) > 1) {
            blockers.add("PREPARATION_MISMATCH");
        }
        if (candidates.stream().anyMatch(BrandedDuplicateRecommendationEngine::missingCoreNutrition)) {
            blockers.add("INCOMPLETE_NUTRITION");
        }
        if (!blockers.isEmpty()) {
            return manual("Market, preparation or nutrition evidence is not compatible.", blockers);
        }

        boolean sameServing = distinct(candidates.stream()
                .map(candidate -> normalized(candidate.servingSize()) + "|" + normalized(candidate.servingUnit()))
                .toList()) == 1;
        boolean nutritionMatches = allNutritionMatches(candidates);
        Long survivor = candidates.stream()
                .max(Comparator
                        .comparing((BrandedProductDuplicateCandidateDto candidate) -> "VERIFIED".equals(candidate.verificationStatus()))
                        .thenComparing(candidate -> Objects.requireNonNullElse(candidate.qualityScore(), -1))
                        .thenComparing(candidate -> candidate.productId() == null ? Long.MIN_VALUE : -candidate.productId()))
                .map(BrandedProductDuplicateCandidateDto::productId)
                .orElse(null);

        if (sameServing && nutritionMatches) {
            return new BrandedDuplicateRecommendationDto(
                    "COLLAPSE_DUPLICATES", 99, survivor,
                    "Identity, serving and nutrition evidence match; keep one search result while retaining all GTIN records.",
                    List.of()
            );
        }

        boolean completePackages = candidates.stream().allMatch(candidate ->
                candidate.servingSize() != null && candidate.servingSize() > 0
                        && candidate.servingUnit() != null && !candidate.servingUnit().isBlank());
        boolean samePackageUnit = distinct(candidates.stream()
                .map(BrandedProductDuplicateCandidateDto::servingUnit).toList()) == 1;
        if (completePackages && samePackageUnit && (nutritionMatches || allDensityNutritionMatches(candidates))) {
            return new BrandedDuplicateRecommendationDto(
                    "CREATE_PACKAGE_FAMILY", 96, survivor,
                    "Identity and nutrition match while package quantities differ; expose one family with package variants.",
                    List.of()
            );
        }
        if (!completePackages) {
            return manual("Package quantities are incomplete.", List.of("INCOMPLETE_PACKAGE_SIZE"));
        }
        if (!samePackageUnit) {
            return manual("Package measurement units are not comparable.", List.of("PACKAGE_UNIT_MISMATCH"));
        }
        return manual("Nutrition profiles differ beyond the safe tolerance.", List.of("NUTRITION_MISMATCH"));
    }

    private static BrandedDuplicateRecommendationDto manual(String reason, List<String> blockers) {
        return new BrandedDuplicateRecommendationDto("MANUAL_REVIEW", 0, null, reason, blockers);
    }

    private static boolean missingCoreNutrition(BrandedProductDuplicateCandidateDto candidate) {
        return candidate.calories() == null || candidate.protein() == null
                || candidate.carbs() == null || candidate.fat() == null;
    }

    private static boolean allNutritionMatches(List<BrandedProductDuplicateCandidateDto> candidates) {
        BrandedProductDuplicateCandidateDto baseline = candidates.get(0);
        return candidates.stream().skip(1).allMatch(candidate ->
                close(baseline.calories(), candidate.calories())
                        && close(baseline.protein(), candidate.protein())
                        && close(baseline.carbs(), candidate.carbs())
                        && close(baseline.fat(), candidate.fat()));
    }

    private static boolean allDensityNutritionMatches(List<BrandedProductDuplicateCandidateDto> candidates) {
        BrandedProductDuplicateCandidateDto baseline = candidates.get(0);
        return candidates.stream().skip(1).allMatch(candidate ->
                densityClose(baseline.calories(), baseline.servingSize(), candidate.calories(), candidate.servingSize())
                        && densityClose(baseline.protein(), baseline.servingSize(), candidate.protein(), candidate.servingSize())
                        && densityClose(baseline.carbs(), baseline.servingSize(), candidate.carbs(), candidate.servingSize())
                        && densityClose(baseline.fat(), baseline.servingSize(), candidate.fat(), candidate.servingSize()));
    }

    private static boolean densityClose(Double left, Double leftSize, Double right, Double rightSize) {
        if (left == null || right == null || leftSize == null || rightSize == null || leftSize <= 0 || rightSize <= 0) {
            return false;
        }
        return close(left / leftSize, right / rightSize);
    }

    private static boolean close(Double left, Double right) {
        if (left == null || right == null) return false;
        double scale = Math.max(1d, Math.max(Math.abs(left), Math.abs(right)));
        return Math.abs(left - right) / scale <= NUTRITION_TOLERANCE;
    }

    private static long distinct(List<?> values) {
        return values.stream().map(BrandedDuplicateRecommendationEngine::normalized).distinct().count();
    }

    private static String normalized(Object value) {
        return value == null ? "<missing>" : value.toString().trim().toUpperCase();
    }
}
