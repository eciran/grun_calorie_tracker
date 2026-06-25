package com.grun.calorietracker.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Reason for reporting a public recipe.")
public enum RecipeReportReason {
    INCORRECT_NUTRITION,
    UNSAFE_INSTRUCTIONS,
    INAPPROPRIATE_CONTENT,
    COPYRIGHT_OR_BRAND,
    SPAM,
    OTHER
}
