package com.grun.calorietracker.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Supported third-party health data providers.")
public enum HealthProvider {
    APPLE_HEALTH,
    GOOGLE_FIT,
    HEALTH_CONNECT
}
