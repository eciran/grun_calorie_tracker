package com.grun.calorietracker.mapper;

import com.grun.calorietracker.dto.FoodServingOptionDto;
import com.grun.calorietracker.entity.FoodItemServingOptionEntity;

public final class FoodServingOptionMapper {

    private FoodServingOptionMapper() {
    }

    public static FoodServingOptionDto toDto(FoodItemServingOptionEntity entity) {
        if (entity == null) {
            return null;
        }
        FoodServingOptionDto dto = new FoodServingOptionDto();
        dto.setId(entity.getId());
        dto.setFoodItemId(entity.getFoodItem() == null ? null : entity.getFoodItem().getId());
        dto.setLabel(entity.getLabel());
        dto.setUnitType(entity.getUnitType());
        dto.setQuantity(entity.getQuantity());
        dto.setGramWeight(entity.getGramWeight());
        dto.setMlVolume(entity.getMlVolume());
        dto.setDefaultOption(entity.getIsDefault());
        dto.setSource(entity.getSource());
        dto.setQualityStatus(entity.getQualityStatus());
        return dto;
    }
}
