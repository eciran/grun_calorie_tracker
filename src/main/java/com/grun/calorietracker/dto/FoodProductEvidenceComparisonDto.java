package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodEvidenceBasis;
import com.grun.calorietracker.enums.FoodEvidenceComparisonState;
import com.grun.calorietracker.enums.FoodEvidenceField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FoodProductEvidenceComparisonDto {
    private FoodEvidenceField fieldName;
    private FoodEvidenceBasis basis;
    private FoodEvidenceComparisonState state;
    private Long preferredEvidenceId;
    private Double maximumDifference;
    private String reason;
    private List<Long> evidenceIds;
}