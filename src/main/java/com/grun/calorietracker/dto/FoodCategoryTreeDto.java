package com.grun.calorietracker.dto;

import java.util.List;

public record FoodCategoryTreeDto(
        Long id,
        String slug,
        String nameEn,
        String nameTr,
        String descriptionEn,
        String descriptionTr,
        String iconKey,
        String imageUrl,
        int sortOrder,
        List<FoodCategoryTreeDto> children
) { }
