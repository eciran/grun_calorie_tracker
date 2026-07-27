package com.grun.calorietracker.dto;

import java.util.List;

public record AdminPromoRedemptionPageDto(
        List<AdminPromoRedemptionRowDto> content,
        int page, int size, long totalElements, int totalPages,
        boolean first, boolean last
) {
}
