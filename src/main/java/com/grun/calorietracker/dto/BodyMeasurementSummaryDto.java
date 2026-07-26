package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Basic body measurement summary without predictive or Pro analytics.")
public class BodyMeasurementSummaryDto {
    private long recordCount;
    private BodyMeasurementDto latest;
    private Double currentWeightKg;
    private Double previousWeightKg;
    private Double weightChangeKg;
    private Double currentBodyFatPercentage;
    private Double previousBodyFatPercentage;
    private Double bodyFatChangePercentagePoints;
    private Double currentBmi;
}
