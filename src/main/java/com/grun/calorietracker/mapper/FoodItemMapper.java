package com.grun.calorietracker.mapper;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.service.support.NutritionValueNormalizer;

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
        entity.setSourceKey(dto.getSourceKey());
        entity.setName(dto.getProductName());
        entity.setImageUrl(dto.getImageUrl());
        entity.setExternalImageUrl(dto.getExternalImageUrl());
        entity.setDisplayImageUrl(dto.getDisplayImageUrl());
        entity.setDataSource(dto.getDataSource());
        entity.setCatalogType(dto.getCatalogType());
        entity.setVerificationStatus(dto.getVerificationStatus());
        entity.setImageSource(dto.getImageSource());
        entity.setImageStatus(dto.getImageStatus());
        entity.setMarketRegion(dto.getMarketRegion());
        entity.setUsageCount(dto.getUsageCount());
        entity.setQualityScore(dto.getQualityScore());
        entity.setReviewPriority(dto.getReviewPriority());
        entity.setLastExternalSyncAt(parseLocalDateTime(dto.getLastExternalSyncAt()));
        entity.setLastReviewedAt(parseLocalDateTime(dto.getLastReviewedAt()));
        entity.setReviewedBy(dto.getReviewedBy());
        entity.setCalories(NutritionValueNormalizer.calories(dto.getCalories()));
        entity.setProtein(NutritionValueNormalizer.macro(dto.getProtein()));
        entity.setFat(NutritionValueNormalizer.macro(dto.getFat()));
        entity.setCarbs(NutritionValueNormalizer.macro(dto.getCarbs()));
        entity.setFiber(NutritionValueNormalizer.macro(dto.getFiber()));
        entity.setSugar(NutritionValueNormalizer.macro(dto.getSugar()));
        entity.setSodium(NutritionValueNormalizer.sodium(dto.getSodium()));
        entity.setServingSizeGrams(NutritionValueNormalizer.servingSize(dto.getServingSize()));
        entity.setServingUnit(dto.getServingUnit());
        entity.setAllergens(dto.getAllergens());
        entity.setNutriScore(dto.getNutriScore());
        entity.setIsCustom(dto.getCustom());
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
        dto.setSourceKey(entity.getSourceKey());
        dto.setProductName(entity.getName());
        // FoodItemEntity does not currently store brand.
        dto.setImageUrl(entity.getImageUrl());
        dto.setExternalImageUrl(entity.getExternalImageUrl());
        dto.setDisplayImageUrl(entity.getDisplayImageUrl());
        dto.setDataSource(entity.getDataSource());
        dto.setCatalogType(entity.getCatalogType());
        dto.setVerificationStatus(entity.getVerificationStatus());
        dto.setImageSource(entity.getImageSource());
        dto.setImageStatus(entity.getImageStatus());
        dto.setMarketRegion(entity.getMarketRegion());
        dto.setUsageCount(entity.getUsageCount());
        dto.setQualityScore(entity.getQualityScore());
        dto.setReviewPriority(entity.getReviewPriority());
        dto.setLastExternalSyncAt(formatLocalDateTime(entity.getLastExternalSyncAt()));
        dto.setLastReviewedAt(formatLocalDateTime(entity.getLastReviewedAt()));
        dto.setReviewedBy(entity.getReviewedBy());
        dto.setCalories(NutritionValueNormalizer.calories(entity.getCalories()));
        dto.setProtein(NutritionValueNormalizer.macro(entity.getProtein()));
        dto.setFat(NutritionValueNormalizer.macro(entity.getFat()));
        dto.setCarbs(NutritionValueNormalizer.macro(entity.getCarbs()));
        dto.setFiber(NutritionValueNormalizer.macro(entity.getFiber()));
        dto.setSugar(NutritionValueNormalizer.macro(entity.getSugar()));
        dto.setSodium(NutritionValueNormalizer.sodium(entity.getSodium()));
        dto.setServingSize(NutritionValueNormalizer.servingSize(entity.getServingSizeGrams()));
        dto.setServingUnit(entity.getServingUnit());
        // FoodItemEntity does not currently store ingredientsText.
        // dto.setIngredientsText(entity.getIngredientsText());
        dto.setAllergens(entity.getAllergens());
        dto.setNutriScore(entity.getNutriScore());
        dto.setCustom(entity.getIsCustom());
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
