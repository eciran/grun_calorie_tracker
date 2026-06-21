package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.FoodPortionUnit;
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
        entity.setPotassium(0.35849);
        entity.setCalcium(0.18249);
        entity.setIron(0.004567);
        entity.setVitaminA(0.0009123);
        entity.setSaturatedFat(7.55555);
        entity.setTransFat(0.04444);
        entity.setServingSizeGrams(48.88888);

        FoodProductDto dto = FoodItemMapper.mapEntityToDto(entity);

        assertEquals(481.2, dto.getCalories());
        assertEquals(8.7, dto.getProtein());
        assertEquals(22.6, dto.getFat());
        assertEquals(60.5, dto.getCarbs());
        assertEquals(2.3, dto.getFiber());
        assertEquals(51.6, dto.getSugar());
        assertEquals(0.043, dto.getSodium());
        assertEquals(0.358, dto.getPotassium());
        assertEquals(0.182, dto.getCalcium());
        assertEquals(0.005, dto.getIron());
        assertEquals(0.001, dto.getVitaminA());
        assertEquals(7.6, dto.getSaturatedFat());
        assertEquals(0.0, dto.getTransFat());
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
        dto.setPotassium(0.35789);
        dto.setCalcium(0.18349);
        dto.setIron(0.004567);
        dto.setVitaminA(0.0009123);
        dto.setSaturatedFat(10.55555);
        dto.setTransFat(0.04444);
        dto.setServingSize(15.55555);

        FoodItemEntity entity = FoodItemMapper.mapDtoToEntity(dto);

        assertEquals(539.4, entity.getCalories());
        assertEquals(6.3, entity.getProtein());
        assertEquals(31.0, entity.getFat());
        assertEquals(57.5, entity.getCarbs());
        assertEquals(3.5, entity.getFiber());
        assertEquals(56.3, entity.getSugar());
        assertEquals(0.107, entity.getSodium());
        assertEquals(0.358, entity.getPotassium());
        assertEquals(0.183, entity.getCalcium());
        assertEquals(0.005, entity.getIron());
        assertEquals(0.001, entity.getVitaminA());
        assertEquals(10.6, entity.getSaturatedFat());
        assertEquals(0.0, entity.getTransFat());
        assertEquals(15.6, entity.getServingSizeGrams());
    }
    @Test
    void productResponseForSolidFoodDoesNotExposeMilliliterPortionUnit() {
        FoodItemEntity entity = new FoodItemEntity();
        entity.setName("Chicken breast");
        entity.setServingUnit("g");

        FoodProductDto dto = FoodItemMapper.mapEntityToDto(entity);

        assertEquals(java.util.List.of(FoodPortionUnit.GRAM, FoodPortionUnit.SERVING), dto.getAllowedPortionUnits());
        assertEquals(FoodPortionUnit.GRAM, dto.getDefaultPortionUnit());
    }

    @Test
    void productResponseForLiquidFoodExposesMilliliterAsDefaultPortionUnit() {
        FoodItemEntity entity = new FoodItemEntity();
        entity.setName("Milk");
        entity.setServingUnit("ml");

        FoodProductDto dto = FoodItemMapper.mapEntityToDto(entity);

        assertEquals(java.util.List.of(FoodPortionUnit.MILLILITER, FoodPortionUnit.SERVING), dto.getAllowedPortionUnits());
        assertEquals(FoodPortionUnit.MILLILITER, dto.getDefaultPortionUnit());
    }

    @Test
    void productResponseForCountableFoodExposesPieceAsDefaultPortionUnit() {
        FoodItemEntity entity = new FoodItemEntity();
        entity.setName("Egg");
        entity.setServingUnit("piece");

        FoodProductDto dto = FoodItemMapper.mapEntityToDto(entity);

        assertEquals(java.util.List.of(FoodPortionUnit.PIECE, FoodPortionUnit.GRAM, FoodPortionUnit.SERVING), dto.getAllowedPortionUnits());
        assertEquals(FoodPortionUnit.PIECE, dto.getDefaultPortionUnit());
    }
}
