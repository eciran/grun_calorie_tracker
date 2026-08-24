package com.grun.calorietracker.dto;

import java.time.LocalDateTime;

public record AdminProductIntakeAssignmentDto(
        Long caseId,
        String assignedAdminEmail,
        LocalDateTime reviewClaimedAt
) {
}