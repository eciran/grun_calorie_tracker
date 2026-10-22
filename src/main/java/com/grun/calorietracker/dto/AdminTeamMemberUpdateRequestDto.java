package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.enums.AdminPermission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record AdminTeamMemberUpdateRequestDto(
        @NotNull UserRole role,
        Set<AdminPermission> permissions,
        @NotNull Boolean enabled,
        @NotNull Boolean mfaEnabled,
        @NotBlank @Size(max = 500) String reason
) {
    public AdminTeamMemberUpdateRequestDto(UserRole role, Boolean enabled, Boolean mfaEnabled, String reason) {
        this(role, null, enabled, mfaEnabled, reason);
    }
}
