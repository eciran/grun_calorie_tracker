package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.MealPlanDto;
import com.grun.calorietracker.dto.MealPlanItemConsumptionDto;
import com.grun.calorietracker.dto.MealPlanItemLogRequestDto;
import com.grun.calorietracker.dto.MealPlanItemReplaceRequestDto;
import com.grun.calorietracker.dto.MealPlanTodayDto;

import java.time.LocalDate;

public interface MealPlanTrackingService {
    MealPlanDto activate(String email, Long planId);
    MealPlanDto deactivate(String email, Long planId);
    MealPlanTodayDto getActiveForDate(String email, LocalDate date);
    MealPlanItemConsumptionDto logItem(String email, Long planId, Long itemId,
                                       String idempotencyKey, MealPlanItemLogRequestDto request);
    MealPlanItemConsumptionDto skipItem(String email, Long planId, Long itemId,
                                        String idempotencyKey);
    MealPlanItemConsumptionDto replaceItem(String email, Long planId, Long itemId,
                                           String idempotencyKey, MealPlanItemReplaceRequestDto request);
}
