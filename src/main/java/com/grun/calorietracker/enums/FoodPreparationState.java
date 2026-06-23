package com.grun.calorietracker.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Preparation/cooking state used to distinguish raw ingredients from cooked or prepared foods.")
public enum FoodPreparationState {
    @Schema(description = "Preparation state is unknown or not applicable.")
    UNSPECIFIED,

    @Schema(description = "Raw or uncooked ingredient.")
    RAW,

    @Schema(description = "Cooked food without a more specific cooking method.")
    COOKED,

    @Schema(description = "Boiled food.")
    BOILED,

    @Schema(description = "Grilled food.")
    GRILLED,

    @Schema(description = "Fried food.")
    FRIED,

    @Schema(description = "Baked food.")
    BAKED,

    @Schema(description = "Roasted food.")
    ROASTED,

    @Schema(description = "Steamed food.")
    STEAMED,

    @Schema(description = "Prepared mixed food or recipe-like dish.")
    PREPARED
}
