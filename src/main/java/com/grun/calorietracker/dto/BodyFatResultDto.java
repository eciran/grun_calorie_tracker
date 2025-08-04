package com.grun.calorietracker.dto;

import lombok.Data;

@Data
public class BodyFatResultDto {

    private Double bmi;
    private Double bodyFat;
    private Double waistCircumference;
    private Double neckCircumference;
    private Double hipCircumference;
}
