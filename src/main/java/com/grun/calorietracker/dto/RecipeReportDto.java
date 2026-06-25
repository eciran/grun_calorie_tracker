package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.RecipeReportReason;
import com.grun.calorietracker.enums.RecipeReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Public recipe report response.")
public class RecipeReportDto {
    private Long id;
    private Long recipeId;
    private RecipeReportReason reason;
    private RecipeReportStatus status;
    private LocalDateTime createdAt;
}
