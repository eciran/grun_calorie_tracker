package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminTeamMemberUpdateRequestDto(
        @NotNull UserRole role,
        @NotNull Boolean enabled,
        @NotNull Boolean mfaEnabled,
        @NotBlank @Size(max = 500) String reason
) {
}
