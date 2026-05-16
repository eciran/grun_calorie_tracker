package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Password reset confirmation payload containing the reset token and the new password.")
public class PasswordResetConfirmRequestDto {

    @NotBlank
    @Schema(description = "Raw password reset token received by email.", example = "Qh3k9_xxY8Pq...")
    private String token;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, one number and one special character"
    )
    @Schema(description = "New password with at least 8 characters, uppercase, lowercase, number, and special character.", example = "NewStrongPass1!")
    private String newPassword;
}
