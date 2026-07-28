package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.GdprRequestStatus;
import com.grun.calorietracker.enums.GdprRequestType;

import java.time.Instant;

public record AdminGdprRequestDto(
        Long id,
        GdprRequestType requestType,
        GdprRequestStatus status,
        String subjectReference,
        Instant requestedAt,
        Instant dueAt,
        Instant completedAt,
        String assignedTo,
        String resultCode,
        String evidenceReference,
        String failureSummary,
        Instant escalatedAt
) {
}
