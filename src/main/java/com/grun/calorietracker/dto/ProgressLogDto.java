package com.grun.calorietracker.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProgressLogDto {

    private Long id;
    private LocalDateTime logDate;
    @NotNull(message = "Weight is required")
    @Min(value = 20, message = "Weight must be at least 20 kg")
    private Double weight;
    private Integer calorieIntake;
    private Double proteinIntake;
    private Double fatIntake;
    private Double carbIntake;
    private String note;
}