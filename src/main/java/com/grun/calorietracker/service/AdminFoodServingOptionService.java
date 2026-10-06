package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminFoodServingOptionUpsertRequestDto;
import com.grun.calorietracker.dto.AdminProductQualityWorkbenchDto;

public interface AdminFoodServingOptionService {
    AdminProductQualityWorkbenchDto.ServingOptionDto create(
            Long productId,
            AdminFoodServingOptionUpsertRequestDto request,
            String reviewedBy
    );

    AdminProductQualityWorkbenchDto.ServingOptionDto update(
            Long productId,
            Long optionId,
            AdminFoodServingOptionUpsertRequestDto request,
            String reviewedBy
    );
}
