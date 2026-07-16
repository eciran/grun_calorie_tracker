package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiMealDraftRejectRequestDto;
import com.grun.calorietracker.dto.AiNutritionPlanConfirmRequestDto;
import com.grun.calorietracker.dto.AiNutritionPlanCreditEstimateDto;
import com.grun.calorietracker.dto.AiNutritionPlanDraftRequestDto;
import com.grun.calorietracker.dto.AiNutritionPlanDraftResponseDto;
import com.grun.calorietracker.dto.MealPlanDto;
import com.grun.calorietracker.enums.NutritionPlanGenerationMode;

public interface AiNutritionPlanService {
    AiNutritionPlanCreditEstimateDto estimateCreditCost(String email, int dayCount, NutritionPlanGenerationMode generationMode);
    AiNutritionPlanDraftResponseDto createDraft(String email, String idempotencyKey, AiNutritionPlanDraftRequestDto request);
    MealPlanDto confirmDraft(String email, Long requestId, AiNutritionPlanConfirmRequestDto request);
    void rejectDraft(String email, Long requestId, AiMealDraftRejectRequestDto request);
}