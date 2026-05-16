package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Password reset operation response.")
public class PasswordResetResponseDto {

    @Schema(description = "Human-readable password reset result.", example = "If the email exists, a password reset link has been sent.")
    private String message;
}
