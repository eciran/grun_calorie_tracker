package com.grun.calorietracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminFoodProductPreflightDto {
    private List<FoodProductDto> duplicateCandidates = new ArrayList<>();
    private List<String> nutritionWarnings = new ArrayList<>();
}
