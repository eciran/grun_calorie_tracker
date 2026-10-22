package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.enums.AdminPermission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record AdminInvitationCreateRequestDto(
        @NotBlank @Email String email,
        @NotNull UserRole role,
        Set<AdminPermission> permissions
) {
    public AdminInvitationCreateRequestDto(String email, UserRole role) {
        this(email, role, null);
    }
}
