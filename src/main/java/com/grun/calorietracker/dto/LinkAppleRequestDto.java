package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request to link an Apple identity to the authenticated account.")
public class LinkAppleRequestDto {

    @NotBlank(message = "{validation.apple.id-token.required}")
    @Schema(description = "Apple identity token returned to the client after Sign in with Apple.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String idToken;

    @NotBlank(message = "{validation.apple.nonce.required}")
    @Schema(description = "Nonce sent in the Apple authorization request and expected in the verified identity token.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nonce;
}
