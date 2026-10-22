package com.grun.calorietracker.dto;

import java.time.Instant;
import java.util.List;

public record AdminTeamMemberDto(
        Long id,
        String name,
        String email,
        String role,
        List<String> permissions,
        boolean enabled,
        boolean locked,
        boolean mfaEnabled,
        long activeSessions,
        Instant lastActiveAt,
        Instant roleUpdatedAt
) {
}
