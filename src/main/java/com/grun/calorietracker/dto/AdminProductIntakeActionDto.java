package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodProductReviewCaseStatus;

public record AdminProductIntakeActionDto(
        Long caseId,
        FoodProductReviewCaseStatus status,
        Long foodItemId,
        String assignedAdminEmail
) {
}