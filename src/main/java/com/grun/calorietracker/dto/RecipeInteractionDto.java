package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Current user's interaction state for a recipe.")
public class RecipeInteractionDto {
    private Long recipeId;
    private Boolean saved;
    private Boolean favorite;
    private Integer rating;
    private Long savedCount;
    private Long favoriteCount;
    private Long ratingCount;
    private Double averageRating;
    private LocalDateTime updatedAt;
}
