package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated, read-only branded duplicate candidate response.")
public record BrandedProductDuplicateGroupPageDto(
        List<BrandedProductDuplicateGroupDto> content,
        Integer page,
        Integer size,
        Long totalElements,
        Integer totalPages,
        Boolean first,
        Boolean last
) {
}
