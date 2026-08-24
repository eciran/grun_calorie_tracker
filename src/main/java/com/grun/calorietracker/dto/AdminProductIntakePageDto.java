package com.grun.calorietracker.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record AdminProductIntakePageDto(
        List<AdminProductIntakeSummaryDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static AdminProductIntakePageDto from(Page<AdminProductIntakeSummaryDto> source) {
        return new AdminProductIntakePageDto(source.getContent(), source.getNumber(), source.getSize(),
                source.getTotalElements(), source.getTotalPages(), source.isFirst(), source.isLast());
    }
}