package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Password reset request payload.")
public class PasswordResetRequestDto {

    @NotBlank
    @Email
    @Schema(description = "Email address of the account requesting a password reset.", example = "user@example.com")
    private String email;
}
