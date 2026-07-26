package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Locale;

@Data
@Schema(description = "Email verification resend request payload.")
public class EmailVerificationRequestDto {

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    @Size(max = 254, message = "{validation.email.invalid}")
    @Schema(description = "Email address of the account that should receive a verification link.", example = "user@example.com")
    private String email;

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
