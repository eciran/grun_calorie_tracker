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
    @NotNull(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    @Schema(description = "User email address.", example = "user@example.com")
    private String email;

    @NotNull(message = "{validation.password.required}")
    @Size(min = 8, message = "{validation.password.size}")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
            message = "{validation.password.pattern}"
    )
    @Schema(description = "Password with at least 8 characters, uppercase, lowercase, number, and special character.", example = "StrongPass1!")
    private String password;
}
