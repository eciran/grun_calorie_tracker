package com.grun.calorietracker.dto;

import java.time.Instant;

public record AdminSessionDto(
        String id, String device, String maskedIp, Instant createdAt, Instant lastActivityAt,
        Instant idleExpiresAt, Instant absoluteExpiresAt, boolean current
) {}
