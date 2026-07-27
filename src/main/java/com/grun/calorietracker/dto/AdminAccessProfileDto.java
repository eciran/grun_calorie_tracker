package com.grun.calorietracker.dto;

import java.util.List;

public record AdminAccessProfileDto(
        Long userId,
        String email,
        String role,
        List<String> permissions,
        boolean mfaRequired,
        boolean mfaEnabled
) {
}
