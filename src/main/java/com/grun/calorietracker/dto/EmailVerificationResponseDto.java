package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Email verification operation response.")
public class EmailVerificationResponseDto {

    @Schema(description = "Human-readable email verification result.", example = "If the email exists, a verification link has been sent.")
    private String message;
}
