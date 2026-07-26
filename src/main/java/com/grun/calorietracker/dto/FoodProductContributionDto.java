package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodProductContributionStatus;
import com.grun.calorietracker.enums.MarketRegion;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Data
public class FoodProductContributionDto {
    private Long id;
    private Long submittedByUserId;
    private String barcode;
    private String productName;
    private String brand;
    private MarketRegion marketRegion;
    private Double calories;
    private Double protein;
    private Double fat;
    private Double carbs;
    private Double fiber;
    private Double sugar;
    private Double sodium;
    private Double servingSizeGrams;
    private String servingUnit;
    private String evidenceUrl;
    private String evidenceContentType;
    private Long evidenceSizeBytes;
    private String evidenceChecksum;
    private OffsetDateTime evidenceRetrievedAt;
    private Boolean commercialUseAllowed;
    private Boolean persistentStorageAllowed;
    private FoodProductContributionStatus status;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
