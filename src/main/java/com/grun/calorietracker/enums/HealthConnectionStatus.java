package com.grun.calorietracker.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Third-party health connection status for a user.")
public enum HealthConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    REVOKED
}
