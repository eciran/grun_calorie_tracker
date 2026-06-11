package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.dto.MealTemplateApplyRequestDto;
import com.grun.calorietracker.dto.MealTemplateCreateRequestDto;
import com.grun.calorietracker.dto.MealTemplateDto;
import com.grun.calorietracker.dto.MealTemplateUpdateRequestDto;

import java.util.List;

public interface MealTemplateService {
    MealTemplateDto createFromLoggedMeal(String email, MealTemplateCreateRequestDto request);
    MealTemplateDto updateTemplate(String email, Long templateId, MealTemplateUpdateRequestDto request);
    List<MealTemplateDto> getTemplates(String email, int page, int size);
    List<FoodLogsDto> applyTemplate(String email, Long templateId, MealTemplateApplyRequestDto request);
    void deleteTemplate(String email, Long templateId);
}
