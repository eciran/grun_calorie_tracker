package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Password reset confirmation payload containing the reset token and the new password.")
public class PasswordResetConfirmRequestDto {

    @NotBlank(message = "{validation.password-reset.token.required}")
    @Schema(description = "Raw password reset token received by email.", example = "Qh3k9_xxY8Pq...")
    private String token;

    @NotBlank(message = "{validation.password.required}")
    @Size(min = 8, message = "{validation.password.size}")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
            message = "{validation.password.pattern}"
    )
    @Schema(description = "New password with at least 8 characters, uppercase, lowercase, number, and special character.", example = "NewStrongPass1!")
    private String newPassword;
}
