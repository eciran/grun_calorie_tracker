package com.grun.calorietracker.dto;

import java.time.Instant;

public record AdminTeamMemberDto(
        Long id,
        String name,
        String email,
        String role,
        boolean enabled,
        boolean locked,
        boolean mfaEnabled,
        long activeSessions,
        Instant lastActiveAt,
        Instant roleUpdatedAt
) {
}
