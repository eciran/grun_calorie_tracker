package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.BodyMeasurementUnitSystem;
import com.grun.calorietracker.enums.HealthProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Body measurement input. Metric values use kilograms and centimetres; imperial values use pounds and inches.")
public class BodyMeasurementRequestDto {

    @NotNull(message = "{validation.body-measurement.recorded-at.required}")
    private LocalDateTime recordedAt;

    @DecimalMin(value = "20.0", message = "{validation.body-measurement.weight.min}")
    @DecimalMax(value = "1100.0", message = "{validation.body-measurement.weight.max}")
    private Double weight;

    @DecimalMin(value = "0.0", message = "{validation.body-measurement.body-fat.min}")
    @DecimalMax(value = "80.0", message = "{validation.body-measurement.body-fat.max}")
    private Double bodyFatPercentage;

    @DecimalMin(value = "4.0", message = "{validation.body-measurement.length.min}")
    @DecimalMax(value = "300.0", message = "{validation.body-measurement.length.max}")
    private Double waist;

    @DecimalMin(value = "4.0", message = "{validation.body-measurement.length.min}")
    @DecimalMax(value = "300.0", message = "{validation.body-measurement.length.max}")
    private Double chest;

    @DecimalMin(value = "4.0", message = "{validation.body-measurement.length.min}")
    @DecimalMax(value = "300.0", message = "{validation.body-measurement.length.max}")
    private Double hip;

    @DecimalMin(value = "4.0", message = "{validation.body-measurement.length.min}")
    @DecimalMax(value = "300.0", message = "{validation.body-measurement.length.max}")
    private Double upperArm;

    @DecimalMin(value = "4.0", message = "{validation.body-measurement.length.min}")
    @DecimalMax(value = "300.0", message = "{validation.body-measurement.length.max}")
    private Double thigh;

    @DecimalMin(value = "4.0", message = "{validation.body-measurement.length.min}")
    @DecimalMax(value = "300.0", message = "{validation.body-measurement.length.max}")
    private Double neck;

    @DecimalMin(value = "4.0", message = "{validation.body-measurement.length.min}")
    @DecimalMax(value = "300.0", message = "{validation.body-measurement.length.max}")
    private Double shoulder;

    @DecimalMin(value = "2.0", message = "{validation.body-measurement.length.min}")
    @DecimalMax(value = "150.0", message = "{validation.body-measurement.length.max}")
    private Double forearm;

    @DecimalMin(value = "2.0", message = "{validation.body-measurement.length.min}")
    @DecimalMax(value = "200.0", message = "{validation.body-measurement.length.max}")
    private Double calf;

    @DecimalMin(value = "2.0", message = "{validation.body-measurement.length.min}")
    @DecimalMax(value = "150.0", message = "{validation.body-measurement.length.max}")
    private Double leftUpperArm;

    @DecimalMin(value = "2.0", message = "{validation.body-measurement.length.min}")
    @DecimalMax(value = "150.0", message = "{validation.body-measurement.length.max}")
    private Double rightUpperArm;

    @DecimalMin(value = "4.0", message = "{validation.body-measurement.length.min}")
    @DecimalMax(value = "200.0", message = "{validation.body-measurement.length.max}")
    private Double leftThigh;

    @DecimalMin(value = "4.0", message = "{validation.body-measurement.length.min}")
    @DecimalMax(value = "200.0", message = "{validation.body-measurement.length.max}")
    private Double rightThigh;

    @DecimalMin(value = "2.0", message = "{validation.body-measurement.length.min}")
    @DecimalMax(value = "200.0", message = "{validation.body-measurement.length.max}")
    private Double leftCalf;

    @DecimalMin(value = "2.0", message = "{validation.body-measurement.length.min}")
    @DecimalMax(value = "200.0", message = "{validation.body-measurement.length.max}")
    private Double rightCalf;

    @NotNull(message = "{validation.body-measurement.unit-system.required}")
    private BodyMeasurementUnitSystem unitSystem = BodyMeasurementUnitSystem.METRIC;

    @NotNull(message = "{validation.body-measurement.provider.required}")
    private HealthProvider provider = HealthProvider.MANUAL;

    @Size(max = 255)
    private String externalId;

    @Size(max = 500)
    private String note;

    @AssertTrue(message = "{validation.body-measurement.value.required}")
    @Schema(hidden = true)
    public boolean isMeasurementPresent() {
        return weight != null || bodyFatPercentage != null || waist != null || chest != null
                || hip != null || upperArm != null || thigh != null || neck != null
                || shoulder != null || forearm != null || calf != null
                || leftUpperArm != null || rightUpperArm != null
                || leftThigh != null || rightThigh != null
                || leftCalf != null || rightCalf != null;
    }
}
