package com.grun.calorietracker.dto;

import java.util.List;

public record AdminGdprRequestPageDto(
        List<AdminGdprRequestDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
