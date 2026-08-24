package com.grun.calorietracker.dto;

import java.time.Instant;

public record AdminInvitationDto(
        Long id, String email, String role, String status, String invitedBy,
        Instant expiresAt, Instant acceptedAt, Instant revokedAt, Instant createdAt
) {}
