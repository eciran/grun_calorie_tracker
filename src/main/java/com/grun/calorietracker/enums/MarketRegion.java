package com.grun.calorietracker.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Supported market regions for localized food catalog and user preference.")
public enum MarketRegion {
    @Schema(description = "Global fallback catalog")
    GLOBAL,

    @Schema(description = "Turkey")
    TR,

    @Schema(description = "United Kingdom and Ireland shared catalog")
    UK_IE,

    @Schema(description = "European Union shared catalog")
    EU
}
