package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Quick calorie entry when the user does not want to search/select a product.")
public class QuickCalorieLogRequestDto {

    @NotNull(message = "{validation.food-log.calories.required}")
    @Positive(message = "{validation.food-log.calories.positive}")
    @Schema(description = "Calories to add.", example = "250.0")
    private Double calories;

    @NotBlank(message = "{validation.food-log.meal-type.required}")
    @Pattern(regexp = "(?i)BREAKFAST|LUNCH|DINNER|SNACK", message = "{validation.food-log.meal-type.invalid}")
    private String mealType;

    @NotNull(message = "{validation.food-log.log-date.required}")
    private LocalDateTime logDate;

    @Size(max = 120)
    private String note;
}
