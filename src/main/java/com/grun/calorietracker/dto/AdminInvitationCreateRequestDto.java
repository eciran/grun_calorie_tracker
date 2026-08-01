package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminInvitationCreateRequestDto(
        @NotBlank @Email String email,
        @NotNull UserRole role
) {}
