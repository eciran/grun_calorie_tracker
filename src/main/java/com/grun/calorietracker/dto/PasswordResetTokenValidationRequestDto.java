package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetTokenValidationRequestDto(
        @NotBlank @Size(max = 512) String token) {
}
