package com.grun.calorietracker.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class FoodLogsDto {

    private Long id;

    @NotNull(message = "Food item id is required")
    private Long foodItemId;

    private String foodName;

    @NotNull(message = "Portion size is required")
    @DecimalMin(value = "0.1", inclusive = true, message = "Portion size must be greater than 0")
    private Double portionSize;

    @NotBlank(message = "Meal type is required")
    private String mealType;

    private LocalDateTime logDate;
}