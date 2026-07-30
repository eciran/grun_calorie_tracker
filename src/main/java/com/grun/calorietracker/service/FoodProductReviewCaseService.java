package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import com.grun.calorietracker.enums.FoodProductReviewCaseStatus;
import com.grun.calorietracker.service.model.FoodProductReviewCaseCommand;

public interface FoodProductReviewCaseService {
    FoodProductReviewCaseEntity finalizeCase(FoodProductReviewCaseCommand command);

    FoodProductReviewCaseEntity transition(
            Long caseId,
            FoodProductReviewCaseStatus target,
            String actor,
            String note
    );
}
