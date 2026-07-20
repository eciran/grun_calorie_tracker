package com.grun.calorietracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FoodCanonicalResolutionDto {
    private String canonicalFoodKey;
    private Long primaryProductId;
    private String resolvedBy;
    private String resolvedAt;
}