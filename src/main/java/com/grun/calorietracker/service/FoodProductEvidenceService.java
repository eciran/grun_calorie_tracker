package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductEvidenceComparisonDto;
import com.grun.calorietracker.dto.FoodProductEvidenceContextDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.FoodEvidenceBasis;
import com.grun.calorietracker.enums.FoodEvidenceField;

import java.time.LocalDateTime;
import java.util.List;

public interface FoodProductEvidenceService {
    int recordImportEvidence(
            List<FoodItemEntity> products,
            FoodEvidenceBasis basis,
            LocalDateTime observedAt,
            String sourceVersion,
            String reviewerIdentity
    );
    FoodProductEvidenceContextDto buildContext(FoodItemEntity product);
    FoodProductEvidenceComparisonDto compare(List<FoodItemEntity> products, FoodEvidenceField field, FoodEvidenceBasis basis);
}