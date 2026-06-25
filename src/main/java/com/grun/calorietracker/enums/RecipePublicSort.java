package com.grun.calorietracker.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Public recipe discovery sort mode.")
public enum RecipePublicSort {
    NEWEST,
    POPULAR,
    RATING
}
