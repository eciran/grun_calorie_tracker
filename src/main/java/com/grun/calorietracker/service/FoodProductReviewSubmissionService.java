package com.grun.calorietracker.service;
import com.grun.calorietracker.dto.*;
public interface FoodProductReviewSubmissionService {
 FoodProductReviewSubmitResponseDto submit(String userEmail, String uploadSessionId, FoodProductReviewSubmitRequestDto request);
 FoodProductReviewSubmitResponseDto resubmitEvidence(String userEmail, Long caseId, String uploadSessionId);
}
