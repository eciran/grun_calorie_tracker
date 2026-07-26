package com.grun.calorietracker.dto;

import java.util.List;

public record AdminTeamPageDto(
        List<AdminTeamMemberDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
