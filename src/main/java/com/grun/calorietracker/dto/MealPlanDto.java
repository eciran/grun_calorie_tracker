package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.MealPlanStatus;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<MealPlanItemDto> items;
}
