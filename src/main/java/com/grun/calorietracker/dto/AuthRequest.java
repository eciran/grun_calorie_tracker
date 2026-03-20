package com.grun.calorietracker.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class    AuthRequest {
    @Email
    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    private String email;

    @NotNull
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, one number and one special character"
    )
    private String password;
}
