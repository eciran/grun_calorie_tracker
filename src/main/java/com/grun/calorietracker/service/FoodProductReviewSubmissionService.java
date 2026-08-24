package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductReviewSubmitRequestDto;
import com.grun.calorietracker.dto.FoodProductReviewSubmitResponseDto;
import com.grun.calorietracker.dto.MyFoodProductReviewCasePageDto;

public interface FoodProductReviewSubmissionService {
    FoodProductReviewSubmitResponseDto submit(
            String userEmail, String uploadSessionId, FoodProductReviewSubmitRequestDto request);

    FoodProductReviewSubmitResponseDto resubmitEvidence(
            String userEmail, Long caseId, String uploadSessionId);

    MyFoodProductReviewCasePageDto listMine(String userEmail, int page, int size);

    FoodProductReviewSubmitResponseDto withdraw(String userEmail, Long caseId);
}