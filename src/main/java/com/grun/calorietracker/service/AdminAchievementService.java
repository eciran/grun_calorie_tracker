package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminAchievementDefinitionDto;
import com.grun.calorietracker.dto.AdminAchievementDefinitionRequestDto;
import com.grun.calorietracker.dto.AdminAchievementMetricsDto;

import java.util.List;

public interface AdminAchievementService {
    List<AdminAchievementDefinitionDto> listDefinitions();

    AdminAchievementMetricsDto listMetricKeys();

    AdminAchievementDefinitionDto createDefinition(AdminAchievementDefinitionRequestDto request);

    AdminAchievementDefinitionDto updateDefinition(String code, AdminAchievementDefinitionRequestDto request);

    AdminAchievementDefinitionDto deactivateDefinition(String code);
}
