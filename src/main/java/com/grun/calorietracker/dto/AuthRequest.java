package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Authentication request payload used for user registration and login.")
public class AuthRequest {
    @NotNull
    @Email
    @Schema(description = "User email address.", example = "user@example.com")
    private String email;

    @NotNull
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, one number and one special character"
    )
    @Schema(description = "Password with at least 8 characters, uppercase, lowercase, number, and special character.", example = "StrongPass1!")
    private String password;
}
