package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductFamilyDto;
import com.grun.calorietracker.dto.FoodProductFamilyPreviewDto;
import com.grun.calorietracker.dto.FoodProductFamilyRequestDto;

public interface FoodProductFamilyAdminService {
    FoodProductFamilyPreviewDto preview(FoodProductFamilyRequestDto request);

    FoodProductFamilyDto apply(FoodProductFamilyRequestDto request, String actor);

    FoodProductFamilyDto deactivate(Long familyId, String reason, String actor);
}
