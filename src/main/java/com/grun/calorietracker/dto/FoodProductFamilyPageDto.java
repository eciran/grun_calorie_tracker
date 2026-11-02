package com.grun.calorietracker.dto;

import java.util.List;

public record FoodProductFamilyPageDto(
        List<FoodProductFamilyDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
