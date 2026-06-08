package com.grun.calorietracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RevenueCatMetricCardDto {
    private String key;
    private String label;
    private String value;
    private String unit;
    private String description;
}
