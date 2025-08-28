package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExerciseItemDto {

    private Long id;
    private String name;
    @NotBlank(message = "MET code is required")
    private String metCode;
    @Positive(message = "Calories per minute must be greater than 0")
    private Double caloriesPerMinute;
    private String description;

    private String iconUrl;
}
