package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "User-reviewed AI recipe draft confirmation request.")
public class AiRecipeDraftConfirmRequestDto {
    @Valid
    @NotNull
    @Schema(description = "Final user-reviewed recipe payload to persist.", requiredMode = Schema.RequiredMode.REQUIRED)
    private RecipeRequestDto recipe;
}
