package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request to link a Google identity to the authenticated account.")
public class LinkGoogleRequestDto {

    @NotBlank(message = "{validation.google.id-token.required}")
    @Schema(description = "Google ID token returned to the mobile client after Google sign-in.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String idToken;
}
