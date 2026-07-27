package com.grun.calorietracker.dto;

import java.util.List;

public record AdminPromoPageDto(
        List<AdminPromoDto> content, int page, int size, long totalElements,
        int totalPages, boolean first, boolean last
) {}
