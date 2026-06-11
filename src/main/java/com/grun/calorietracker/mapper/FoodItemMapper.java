package com.grun.calorietracker.mapper;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.ProductQualityLabel;
import com.grun.calorietracker.enums.VerificationStatus;
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
        entity.setPotassium(NutritionValueNormalizer.micronutrient(dto.getPotassium()));
        entity.setCholesterol(NutritionValueNormalizer.micronutrient(dto.getCholesterol()));
        entity.setCalcium(NutritionValueNormalizer.micronutrient(dto.getCalcium()));
        entity.setIron(NutritionValueNormalizer.micronutrient(dto.getIron()));
        entity.setMagnesium(NutritionValueNormalizer.micronutrient(dto.getMagnesium()));
        entity.setZinc(NutritionValueNormalizer.micronutrient(dto.getZinc()));
        entity.setVitaminA(NutritionValueNormalizer.micronutrient(dto.getVitaminA()));
        entity.setVitaminC(NutritionValueNormalizer.micronutrient(dto.getVitaminC()));
        entity.setVitaminD(NutritionValueNormalizer.micronutrient(dto.getVitaminD()));
        entity.setVitaminE(NutritionValueNormalizer.micronutrient(dto.getVitaminE()));
        entity.setVitaminB12(NutritionValueNormalizer.micronutrient(dto.getVitaminB12()));
        entity.setSaturatedFat(NutritionValueNormalizer.macro(dto.getSaturatedFat()));
        entity.setTransFat(NutritionValueNormalizer.macro(dto.getTransFat()));
        entity.setSugarAlcohol(NutritionValueNormalizer.macro(dto.getSugarAlcohol()));
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
        dto.setPotassium(NutritionValueNormalizer.micronutrient(entity.getPotassium()));
        dto.setCholesterol(NutritionValueNormalizer.micronutrient(entity.getCholesterol()));
        dto.setCalcium(NutritionValueNormalizer.micronutrient(entity.getCalcium()));
        dto.setIron(NutritionValueNormalizer.micronutrient(entity.getIron()));
        dto.setMagnesium(NutritionValueNormalizer.micronutrient(entity.getMagnesium()));
        dto.setZinc(NutritionValueNormalizer.micronutrient(entity.getZinc()));
        dto.setVitaminA(NutritionValueNormalizer.micronutrient(entity.getVitaminA()));
        dto.setVitaminC(NutritionValueNormalizer.micronutrient(entity.getVitaminC()));
        dto.setVitaminD(NutritionValueNormalizer.micronutrient(entity.getVitaminD()));
        dto.setVitaminE(NutritionValueNormalizer.micronutrient(entity.getVitaminE()));
        dto.setVitaminB12(NutritionValueNormalizer.micronutrient(entity.getVitaminB12()));
        dto.setSaturatedFat(NutritionValueNormalizer.macro(entity.getSaturatedFat()));
        dto.setTransFat(NutritionValueNormalizer.macro(entity.getTransFat()));
        dto.setSugarAlcohol(NutritionValueNormalizer.macro(entity.getSugarAlcohol()));
        dto.setServingSize(NutritionValueNormalizer.servingSize(entity.getServingSizeGrams()));
        dto.setServingUnit(entity.getServingUnit());
        // FoodItemEntity does not currently store ingredientsText.
        // dto.setIngredientsText(entity.getIngredientsText());
        dto.setAllergens(entity.getAllergens());
        dto.setNutriScore(entity.getNutriScore());
        dto.setCustom(entity.getIsCustom());
        dto.setProductQualityLabel(resolveQualityLabel(entity));
        dto.setProductQualityMessage(resolveQualityMessage(dto.getProductQualityLabel()));
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

    private static ProductQualityLabel resolveQualityLabel(FoodItemEntity entity) {
        if (entity.getVerificationStatus() == VerificationStatus.VERIFIED) {
            return Boolean.TRUE.equals(entity.getIsCustom()) ? ProductQualityLabel.COMMUNITY : ProductQualityLabel.VERIFIED;
        }
        if (entity.getDataSource() == FoodDataSource.OPEN_FOOD_FACTS) {
            return ProductQualityLabel.IMPORTED;
        }
        return ProductQualityLabel.NEEDS_REVIEW;
    }

    private static String resolveQualityMessage(ProductQualityLabel label) {
        return switch (label) {
            case VERIFIED -> "Verified by GRun";
            case COMMUNITY -> "User-created food";
            case IMPORTED -> "Imported data, review recommended";
            case NEEDS_REVIEW -> "Nutrition data needs review";
        };
    }
}
