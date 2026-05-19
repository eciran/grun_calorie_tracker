package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Email verification confirmation payload containing the raw verification token.")
public class EmailVerificationConfirmRequestDto {

    @NotBlank(message = "{validation.email-verification.token.required}")
    @Schema(description = "Raw email verification token received by email.", example = "Qh3k9_xxY8Pq...")
    private String token;
}
