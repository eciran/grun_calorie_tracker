package com.grun.calorietracker.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ProgressLogDto {

    private Long id;

    private LocalDateTime logDate;

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "20.0", message = "Weight must be at least 20 kg")
    private Double weight;

    @DecimalMin(value = "0.0", message = "Calorie intake cannot be negative")
    private Integer calorieIntake;

    @DecimalMin(value = "0.0", message = "Protein intake cannot be negative")
    private Double proteinIntake;

    @DecimalMin(value = "0.0", message = "Fat intake cannot be negative")
    private Double fatIntake;

    @DecimalMin(value = "0.0", message = "Carb intake cannot be negative")
    private Double carbIntake;

    private String note;
}