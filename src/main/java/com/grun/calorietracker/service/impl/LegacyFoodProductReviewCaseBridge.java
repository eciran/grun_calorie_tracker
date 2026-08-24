package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductContributionEntity;
import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import com.grun.calorietracker.entity.ProductCorrectionSuggestionEntity;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.service.FoodProductReviewCaseService;
import com.grun.calorietracker.service.model.FoodProductReviewCaseCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LegacyFoodProductReviewCaseBridge {

    private final FoodProductReviewCaseService reviewCaseService;
    private final ObjectMapper objectMapper;

    public FoodProductReviewCaseEntity linkContribution(FoodProductContributionEntity contribution) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("productName", contribution.getProductName());
        values.put("brand", contribution.getBrand());
        values.put("calories", contribution.getCalories());
        values.put("protein", contribution.getProtein());
        values.put("fat", contribution.getFat());
        values.put("carbs", contribution.getCarbs());
        values.put("fiber", contribution.getFiber());
        values.put("sugar", contribution.getSugar());
        values.put("sodium", contribution.getSodium());
        values.put("servingSizeGrams", contribution.getServingSizeGrams());
        values.put("servingUnit", contribution.getServingUnit());

        return reviewCaseService.finalizeCase(new FoodProductReviewCaseCommand(
                "legacy-contribution:" + contribution.getId(),
                FoodProductReviewCaseSource.USER_OCR,
                contribution.getId().toString(),
                contribution.getSubmittedBy(),
                contribution.getNormalizedBarcode(),
                contribution.getMarketRegion(),
                null,
                contribution.getProductName(),
                contribution.getBrand(),
                contribution.getCalories(),
                contribution.getProtein(),
                contribution.getFat(),
                contribution.getCarbs(),
                contribution.getFiber(),
                contribution.getSugar(),
                contribution.getSodium(),
                FoodNutritionBasis.SOURCE_REPORTED,
                FoodProductReviewRiskLevel.MEDIUM,
                1,
                json(values),
                null,
                null,
                "legacy-v1",
                true,
                false
        ));
    }

    public void syncContributionReview(
            FoodProductContributionEntity contribution,
            String reviewer,
            String note
    ) {
        if (contribution.getReviewCase() == null) {
            return;
        }
        Long caseId = contribution.getReviewCase().getId();
        FoodProductReviewCaseEntity inReview = reviewCaseService.transition(
                caseId,
                FoodProductReviewCaseStatus.IN_REVIEW,
                reviewer,
                null
        );
        FoodProductReviewCaseStatus decision =
                contribution.getStatus() == FoodProductContributionStatus.APPROVED
                        ? FoodProductReviewCaseStatus.APPROVED
                        : FoodProductReviewCaseStatus.REJECTED;
        reviewCaseService.transition(inReview.getId(), decision, reviewer, note);
    }

    public FoodProductReviewCaseEntity linkCorrection(ProductCorrectionSuggestionEntity correction) {
        FoodItemEntity foodItem = correction.getFoodItem();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("calories", correction.getSuggestedCalories());
        values.put("protein", correction.getSuggestedProtein());
        values.put("carbs", correction.getSuggestedCarbs());
        values.put("fat", correction.getSuggestedFat());
        values.put("note", correction.getNote());

        return reviewCaseService.finalizeCase(new FoodProductReviewCaseCommand(
                "legacy-correction:" + correction.getId(),
                FoodProductReviewCaseSource.USER_CORRECTION,
                correction.getId().toString(),
                correction.getUser(),
                foodItem.getNormalizedBarcode() == null ? foodItem.getBarcode() : foodItem.getNormalizedBarcode(),
                foodItem.getMarketRegion() == null ? MarketRegion.GLOBAL : foodItem.getMarketRegion(),
                foodItem.getId(),
                foodItem.getName(),
                foodItem.getBrand(),
                correction.getSuggestedCalories(),
                correction.getSuggestedProtein(),
                correction.getSuggestedFat(),
                correction.getSuggestedCarbs(),
                null,
                null,
                null,
                foodItem.getNutritionBasis(),
                FoodProductReviewRiskLevel.MEDIUM,
                1,
                json(values),
                null,
                json(Map.of("legacyCorrectionId", correction.getId())),
                "legacy-v1",
                correction.getImageUrl() != null,
                false
        ));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Review case snapshot could not be serialized", exception);
        }
    }
}
