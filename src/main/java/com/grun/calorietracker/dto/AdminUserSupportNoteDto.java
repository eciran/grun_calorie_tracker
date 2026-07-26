package com.grun.calorietracker.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminUserSupportNoteDto(
        Long id,
        String note,
        List<String> tags,
        String createdBy,
        LocalDateTime createdAt
) {
}
