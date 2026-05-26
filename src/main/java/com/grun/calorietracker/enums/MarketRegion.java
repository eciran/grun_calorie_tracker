package com.grun.calorietracker.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Supported market regions for localized food catalog and user preference.")
public enum MarketRegion {
    @Schema(description = "Ireland")
    IRL,

    @Schema(description = "Turkey")
    TR,

    @Schema(description = "United Kingdom")
    UK
}
