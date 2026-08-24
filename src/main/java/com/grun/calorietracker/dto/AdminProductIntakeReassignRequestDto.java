package com.grun.calorietracker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AdminProductIntakeReassignRequestDto(
        @NotBlank @Email String adminEmail
) {
}