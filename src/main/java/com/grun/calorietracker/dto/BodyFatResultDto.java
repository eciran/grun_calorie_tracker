package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Calculated body composition result.")
public class BodyFatResultDto {

    @Schema(description = "Calculated body mass index.", example = "23.8")
    private Double bmi;

    @Schema(description = "Calculated body fat percentage.", example = "18.5")
    private Double bodyFat;

    @Schema(description = "Waist circumference used for the calculation.", example = "84.0")
    private Double waistCircumference;

    @Schema(description = "Neck circumference used for the calculation.", example = "39.0")
    private Double neckCircumference;

    @Schema(description = "Hip circumference used for the calculation.", example = "96.0")
    private Double hipCircumference;
}
