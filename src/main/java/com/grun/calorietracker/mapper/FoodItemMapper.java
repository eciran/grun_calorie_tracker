package com.grun.calorietracker.mapper;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.entity.FoodItemEntity;

import java.util.List;
import java.util.stream.Collectors;

public class FoodItemMapper {

    public static FoodItemEntity mapDtoToEntity(FoodProductDto dto) {
        if (dto == null) {
            return null;
        }
        FoodItemEntity entity = new FoodItemEntity();
        entity.setBarcode(dto.getBarcode());
        entity.setName(dto.getProductName());
        entity.setImageUrl(dto.getImageUrl());
        entity.setCalories(dto.getCalories());
        entity.setProtein(dto.getProtein());
        entity.setFat(dto.getFat());
        entity.setCarbs(dto.getCarbs());
        entity.setFiber(dto.getFiber());
        entity.setSugar(dto.getSugar());
        entity.setSodium(dto.getSodium());
        entity.setAllergens(dto.getAllergens());
        entity.setNutriScore(dto.getNutriScore());
        // dto.getServingSize() ve dto.getIngredientsText()
        // entity.setServingSize(dto.getServingSize());
        // entity.setIngredientsText(dto.getIngredientsText());
        return entity;
    }

    public static FoodProductDto mapEntityToDto(FoodItemEntity entity) {
        if (entity == null) {
            return null;
        }
        FoodProductDto dto = new FoodProductDto();
        dto.setBarcode(entity.getBarcode());
        dto.setProductName(entity.getName());
        //  dto.setBrand(entity.getBrand());
        dto.setImageUrl(entity.getImageUrl());
        dto.setCalories(entity.getCalories());
        dto.setProtein(entity.getProtein());
        dto.setFat(entity.getFat());
        dto.setCarbs(entity.getCarbs());
        dto.setFiber(entity.getFiber());
        dto.setSugar(entity.getSugar());
        dto.setSodium(entity.getSodium());
        // dto.setServingSize(entity.getServingSize());
        // dto.setIngredientsText(entity.getIngredientsText());
        dto.setAllergens(entity.getAllergens());
        dto.setNutriScore(entity.getNutriScore());
        return dto;
    }

    public static List<FoodItemEntity> mapDtoListToEntityList(List<FoodProductDto> dtos) {
        if (dtos == null) {
            return null;
        }
        return dtos.stream()
                .map(FoodItemMapper::mapDtoToEntity)
                .collect(Collectors.toList());
    }

    public static List<FoodProductDto> mapEntityListToDtoList(List<FoodItemEntity> entities) {
        if (entities == null) {
            return null;
        }
        return entities.stream()
                .map(FoodItemMapper::mapEntityToDto)
                .collect(Collectors.toList());
    }
}
