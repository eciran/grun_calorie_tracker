package com.grun.calorietracker.dto;

import java.time.Instant;
import java.util.List;

public record AdminInvitationDto(
        Long id, String email, String role, List<String> permissions, String status, String invitedBy,
        Instant expiresAt, Instant acceptedAt, Instant revokedAt, Instant createdAt
) {}
