package com.grun.calorietracker.dto;
import com.grun.calorietracker.enums.FoodProductReviewCaseStatus;
public record FoodProductReviewSubmitResponseDto(
        Long caseId,
        FoodProductReviewCaseStatus status,
        String uploadSessionId,
        boolean replayed,
        Long customFoodId,
        String customFoodName
) {}
