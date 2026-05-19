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
        entity.setNormalizedBarcode(dto.getNormalizedBarcode());
        entity.setName(dto.getProductName());
        entity.setImageUrl(dto.getImageUrl());
        entity.setExternalImageUrl(dto.getExternalImageUrl());
        entity.setDisplayImageUrl(dto.getDisplayImageUrl());
        entity.setDataSource(dto.getDataSource());
        entity.setVerificationStatus(dto.getVerificationStatus());
        entity.setImageSource(dto.getImageSource());
        entity.setImageStatus(dto.getImageStatus());
        entity.setUsageCount(dto.getUsageCount());
        entity.setQualityScore(dto.getQualityScore());
        entity.setReviewPriority(dto.getReviewPriority());
        entity.setLastExternalSyncAt(parseLocalDateTime(dto.getLastExternalSyncAt()));
        entity.setLastReviewedAt(parseLocalDateTime(dto.getLastReviewedAt()));
        entity.setReviewedBy(dto.getReviewedBy());
        entity.setCalories(dto.getCalories());
        entity.setProtein(dto.getProtein());
        entity.setFat(dto.getFat());
        entity.setCarbs(dto.getCarbs());
        entity.setFiber(dto.getFiber());
        entity.setSugar(dto.getSugar());
        entity.setSodium(dto.getSodium());
        entity.setServingSizeGrams(dto.getServingSize());
        entity.setServingUnit(dto.getServingUnit());
        entity.setAllergens(dto.getAllergens());
        entity.setNutriScore(dto.getNutriScore());
        // FoodItemEntity does not currently store ingredientsText.
        // entity.setIngredientsText(dto.getIngredientsText());
        return entity;
    }

    public static FoodProductDto mapEntityToDto(FoodItemEntity entity) {
        if (entity == null) {
            return null;
        }
        FoodProductDto dto = new FoodProductDto();
        dto.setId(entity.getId());
        dto.setBarcode(entity.getBarcode());
        dto.setNormalizedBarcode(entity.getNormalizedBarcode());
        dto.setProductName(entity.getName());
        // FoodItemEntity does not currently store brand.
        dto.setImageUrl(entity.getImageUrl());
        dto.setExternalImageUrl(entity.getExternalImageUrl());
        dto.setDisplayImageUrl(entity.getDisplayImageUrl());
        dto.setDataSource(entity.getDataSource());
        dto.setVerificationStatus(entity.getVerificationStatus());
        dto.setImageSource(entity.getImageSource());
        dto.setImageStatus(entity.getImageStatus());
        dto.setUsageCount(entity.getUsageCount());
        dto.setQualityScore(entity.getQualityScore());
        dto.setReviewPriority(entity.getReviewPriority());
        dto.setLastExternalSyncAt(formatLocalDateTime(entity.getLastExternalSyncAt()));
        dto.setLastReviewedAt(formatLocalDateTime(entity.getLastReviewedAt()));
        dto.setReviewedBy(entity.getReviewedBy());
        dto.setCalories(entity.getCalories());
        dto.setProtein(entity.getProtein());
        dto.setFat(entity.getFat());
        dto.setCarbs(entity.getCarbs());
        dto.setFiber(entity.getFiber());
        dto.setSugar(entity.getSugar());
        dto.setSodium(entity.getSodium());
        dto.setServingSize(entity.getServingSizeGrams());
        dto.setServingUnit(entity.getServingUnit());
        // FoodItemEntity does not currently store ingredientsText.
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

    private static java.time.LocalDateTime parseLocalDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return java.time.LocalDateTime.parse(value.trim());
    }

    private static String formatLocalDateTime(java.time.LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
