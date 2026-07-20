package com.grun.calorietracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FoodProductEvidenceContextDto {
    private List<FoodProductEvidenceDto> evidence;
    private List<FoodProductEvidenceComparisonDto> comparisons;
}