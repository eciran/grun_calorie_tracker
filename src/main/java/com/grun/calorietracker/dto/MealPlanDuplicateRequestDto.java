package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Request for copying an existing meal plan into a new date range.")
public class MealPlanDuplicateRequestDto {

    @NotBlank
    @Schema(description = "Name of the copied meal plan.", example = "Next week balanced plan")
    private String name;

    @NotNull
    @Schema(description = "New start date. Items keep their original day offsets.", example = "2026-06-15")
    private LocalDate startDate;
}
