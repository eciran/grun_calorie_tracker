package com.grun.calorietracker.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Moderation status for a public recipe report.")
public enum RecipeReportStatus {
    OPEN,
    REVIEWED,
    DISMISSED
}
