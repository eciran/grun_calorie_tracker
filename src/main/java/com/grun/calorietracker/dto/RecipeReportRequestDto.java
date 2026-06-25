package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.RecipeReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request for reporting a public recipe to moderation.")
public class RecipeReportRequestDto {
    @NotNull
    @Schema(description = "Report reason.", example = "INCORRECT_NUTRITION", requiredMode = Schema.RequiredMode.REQUIRED)
    private RecipeReportReason reason;

    @Size(max = 500)
    @Schema(description = "Optional user note for moderation.", example = "Calories look too low for the ingredient list.")
    private String note;
}
