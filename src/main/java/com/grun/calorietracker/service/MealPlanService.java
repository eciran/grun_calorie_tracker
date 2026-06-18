package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.GroceryListDto;
import com.grun.calorietracker.dto.MealPlanDto;
import com.grun.calorietracker.dto.MealPlanDuplicateRequestDto;
import com.grun.calorietracker.dto.MealPlanRequestDto;

import java.util.List;

public interface MealPlanService {
    MealPlanDto createMealPlan(String email, MealPlanRequestDto request);
    MealPlanDto updateMealPlan(String email, Long planId, MealPlanRequestDto request);
    List<MealPlanDto> getMealPlans(String email);
    MealPlanDto getMealPlan(String email, Long planId);
    MealPlanDto duplicateMealPlan(String email, Long planId, MealPlanDuplicateRequestDto request);
    GroceryListDto getGroceryList(String email, Long planId);
    void archiveMealPlan(String email, Long planId);
}
