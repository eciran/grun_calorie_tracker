package com.grun.calorietracker.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DailySummaryDto {

    private LocalDate summaryDate;

    private Integer targetCalories;
    private Double consumedCalories;
    private Double burnedCalories;
    private Double remainingCalories;

    private Double targetProtein;
    private Double targetFat;
    private Double targetCarbs;

    private Double consumedProtein;
    private Double consumedFat;
    private Double consumedCarbs;

    private Double currentWeight;
    private Double targetWeight;

    private String goalType;

    private Integer totalExerciseMinutes;
}