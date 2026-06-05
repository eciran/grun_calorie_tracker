package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.mapper.FoodItemMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NutritionValueNormalizerTest {

    @Test
    void normalizesNutritionValuesForProductResponses() {
        FoodItemEntity entity = new FoodItemEntity();
        entity.setCalories(481.23456);
        entity.setProtein(8.66666);
        entity.setFat(22.55555);
        entity.setCarbs(60.54444);
        entity.setFiber(2.34567);
        entity.setSugar(51.55555);
        entity.setSodium(0.04286);
        entity.setServingSizeGrams(48.88888);

        FoodProductDto dto = FoodItemMapper.mapEntityToDto(entity);

        assertEquals(481.2, dto.getCalories());
        assertEquals(8.7, dto.getProtein());
        assertEquals(22.6, dto.getFat());
        assertEquals(60.5, dto.getCarbs());
        assertEquals(2.3, dto.getFiber());
        assertEquals(51.6, dto.getSugar());
        assertEquals(0.043, dto.getSodium());
        assertEquals(48.9, dto.getServingSize());
    }

    @Test
    void normalizesNutritionValuesWhenMappingDtoToEntity() {
        FoodProductDto dto = new FoodProductDto();
        dto.setCalories(539.44444);
        dto.setProtein(6.33333);
        dto.setFat(30.95555);
        dto.setCarbs(57.54444);
        dto.setFiber(3.45678);
        dto.setSugar(56.34444);
        dto.setSodium(0.10749);
        dto.setServingSize(15.55555);

        FoodItemEntity entity = FoodItemMapper.mapDtoToEntity(dto);

        assertEquals(539.4, entity.getCalories());
        assertEquals(6.3, entity.getProtein());
        assertEquals(31.0, entity.getFat());
        assertEquals(57.5, entity.getCarbs());
        assertEquals(3.5, entity.getFiber());
        assertEquals(56.3, entity.getSugar());
        assertEquals(0.107, entity.getSodium());
        assertEquals(15.6, entity.getServingSizeGrams());
    }
}
