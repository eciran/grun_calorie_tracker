package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Body measurement payload used to calculate body fat and BMI.")
public class BodyFatRequestDto {

        @Schema(description = "Waist circumference in centimeters.", example = "84.0")
        private Double waistCircumference;

        @Schema(description = "Neck circumference in centimeters.", example = "39.0")
        private Double neckCircumference;

        @Schema(description = "Hip circumference in centimeters. Typically required for female body fat calculation.", example = "96.0")
        private Double hipCircumference;

}
