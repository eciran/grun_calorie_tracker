package com.grun.calorietracker.dto;

import jakarta.validation.constraints.Size;

public record AdminGdprRequestUpdateDto(
        @Size(max = 320) String assignedTo,
        boolean escalated,
        @Size(max = 300) String reason
) {
}
