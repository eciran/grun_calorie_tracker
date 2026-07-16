package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.MealPlanStatus;

import com.grun.calorietracker.enums.NutritionPlanGenerationMode;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MealPlanDto {
    private Long id;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private MealPlanStatus status;

    private NutritionPlanGenerationMode generationMode;
    private Long workoutPlanId;
    private Long sourceAiRequestId;
    private String schemaVersion;
    private String promptVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<MealPlanItemDto> items;
}
