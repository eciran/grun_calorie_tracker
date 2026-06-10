package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "AI-generated recipe draft. It is not persisted as a recipe until the user confirms the final recipe request.")
public class AiRecipeDraftResponseDto {
    private Long requestId;
    private AiRequestType requestType;
    private AiRequestStatus status;
    private AiProvider provider;
    private String model;
    private String summary;
    private Boolean reviewRequired = true;
    private Integer aiRemainingThisPeriod;
    private RecipeRequestDto suggestedRecipe;
    private List<AiRecipeIngredientSuggestionDto> suggestedIngredients = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
