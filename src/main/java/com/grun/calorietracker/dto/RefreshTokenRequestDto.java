package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Refresh token request payload.")
public class RefreshTokenRequestDto {

    @NotBlank(message = "{validation.refresh-token.required}")
    @Schema(description = "Raw refresh token returned by login or refresh.", example = "Qh3k9_xxY8Pq...", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;
}
