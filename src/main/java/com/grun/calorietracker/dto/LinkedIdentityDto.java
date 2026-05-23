package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AuthProvider;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "External login provider currently linked to the authenticated user account.")
public record LinkedIdentityDto(
        @Schema(description = "Linked provider.", example = "GOOGLE")
        AuthProvider provider,

        @Schema(description = "Email returned by the provider when available.", example = "user@example.com")
        String providerEmail,

        @Schema(description = "Date and time when this provider was linked.")
        LocalDateTime linkedAt
) {
}
