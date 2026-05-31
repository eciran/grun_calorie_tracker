package com.grun.calorietracker.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Supported user interface languages.")
public enum PreferredLanguage {
    @Schema(description = "English")
    EN,

    @Schema(description = "Turkish")
    TR
}
