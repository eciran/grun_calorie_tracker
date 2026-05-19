package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Logout request payload.")
public class LogoutRequestDto {

    @NotBlank(message = "{validation.refresh-token.required}")
    @Schema(description = "Refresh token for the current device session.", example = "Qh3k9_xxY8Pq...", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;
}
