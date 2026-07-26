package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.HealthProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "Canonical body measurement values stored in kilograms and centimetres.")
public class BodyMeasurementDto {
    private Long id;
    private LocalDateTime recordedAt;
    private Double weightKg;
    private Double bodyFatPercentage;
    private Double waistCm;
    private Double chestCm;
    private Double hipCm;
    private Double upperArmCm;
    private Double thighCm;
    private Double neckCm;
    private Double shoulderCm;
    private Double forearmCm;
    private Double calfCm;
    private Double leftUpperArmCm;
    private Double rightUpperArmCm;
    private Double leftThighCm;
    private Double rightThighCm;
    private Double leftCalfCm;
    private Double rightCalfCm;
    private Double bmi;
    private HealthProvider provider;
    private String externalId;
    private String note;
    private LocalDateTime updatedAt;
}
