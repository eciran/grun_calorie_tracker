package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;

public interface FoodProductReviewCaseEvidenceService {
    int recordAcceptedEvidence(FoodProductReviewCaseEntity reviewCase);
}