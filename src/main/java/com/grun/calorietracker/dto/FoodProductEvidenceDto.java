package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodEvidenceBasis;
import com.grun.calorietracker.enums.FoodEvidenceField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FoodProductEvidenceDto {
    private Long evidenceId;
    private Long productId;
    private FoodDataSource provider;
    private String externalId;
    private FoodEvidenceField fieldName;
    private Double numericValue;
    private FoodEvidenceBasis basis;
    private Integer confidenceScore;
    private LocalDateTime observedAt;
    private boolean stale;
    private String sourceVersion;
    private String reviewerIdentity;
}