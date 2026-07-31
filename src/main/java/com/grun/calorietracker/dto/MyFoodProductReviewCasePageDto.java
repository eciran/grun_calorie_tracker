package com.grun.calorietracker.dto;

import java.util.List;

public record MyFoodProductReviewCasePageDto(
        List<MyFoodProductReviewCaseDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
