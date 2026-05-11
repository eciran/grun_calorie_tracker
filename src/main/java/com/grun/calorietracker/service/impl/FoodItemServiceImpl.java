package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.mapper.FoodItemMapper;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.service.FoodItemService;
import com.grun.calorietracker.service.OpenFoodFactsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FoodItemServiceImpl implements FoodItemService {

    private final FoodItemRepository foodItemRepository;
    private final OpenFoodFactsService openFoodFactsService;

    @Override
    public FoodItemEntity getOrSaveFoodItemByBarcode(String barcode) {
        return foodItemRepository.findByBarcode(barcode)
                .orElseGet(() -> openFoodFactsService.getProductByBarcode(barcode)
                        .map(FoodItemMapper::mapDtoToEntity)
                        .map(foodItemRepository::save)
                        .orElseThrow(() -> new ProductNotFoundException("Product not found for barcode: " + barcode)));
    }

    @Override
    public List<FoodProductDto> searchFoodItems(FoodSearchCriteriaDto criteria) {
        String query = normalize(criteria.getQuery());

        List<FoodProductDto> localResults = FoodItemMapper.mapEntityListToDtoList(
                foodItemRepository.searchFoodItems(
                        query,
                        criteria.getMinCalories(),
                        criteria.getMaxCalories()
                )
        );

        if (localResults == null) {
            localResults = new ArrayList<>();
        }

        List<FoodProductDto> externalResults = List.of();
        if (query != null) {
            externalResults = openFoodFactsService.searchProductsByCriteria(criteria);
        }

        Map<String, FoodProductDto> merged = new LinkedHashMap<>();

        for (FoodProductDto dto : localResults) {
            String key = buildKey(dto);
            merged.putIfAbsent(key, dto);
        }

        for (FoodProductDto dto : externalResults) {
            String key = buildKey(dto);
            merged.putIfAbsent(key, dto);
        }

        return new ArrayList<>(merged.values());
    }

    @Override
    public FoodProductDto addProduct(FoodProductDto dto) {
        validateProduct(dto);

        if (dto.getBarcode() != null && !dto.getBarcode().isBlank()
                && foodItemRepository.existsByBarcode(dto.getBarcode().trim())) {
            throw new IllegalArgumentException("A product with this barcode already exists");
        }

        FoodItemEntity entity = FoodItemMapper.mapDtoToEntity(dto);
        entity.setId(null);
        entity.setBarcode(normalize(dto.getBarcode()));
        entity.setName(normalize(dto.getProductName()));
        // entity.setBrand(normalize(dto.getBrand()));
        entity.setImageUrl(normalize(dto.getImageUrl()));
        // entity.setIngredientsText(normalize(dto.getIngredientsText()));
        entity.setAllergens(normalize(dto.getAllergens()));
        entity.setNutriScore(normalize(dto.getNutriScore()));

        if (entity.getIsCustom() == null) {
            entity.setIsCustom(Boolean.TRUE);
        }

        FoodItemEntity saved = foodItemRepository.save(entity);
        return FoodItemMapper.mapEntityToDto(saved);
    }

    private void validateProduct(FoodProductDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Product request cannot be null");
        }

        if (dto.getProductName() == null || dto.getProductName().isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }

        if (dto.getCalories() == null || dto.getCalories() < 0) {
            throw new IllegalArgumentException("Calories must be 0 or greater");
        }

        if (dto.getProtein() != null && dto.getProtein() < 0) {
            throw new IllegalArgumentException("Protein must be 0 or greater");
        }

        if (dto.getFat() != null && dto.getFat() < 0) {
            throw new IllegalArgumentException("Fat must be 0 or greater");
        }

        if (dto.getCarbs() != null && dto.getCarbs() < 0) {
            throw new IllegalArgumentException("Carbs must be 0 or greater");
        }

        if (dto.getFiber() != null && dto.getFiber() < 0) {
            throw new IllegalArgumentException("Fiber must be 0 or greater");
        }

        if (dto.getSugar() != null && dto.getSugar() < 0) {
            throw new IllegalArgumentException("Sugar must be 0 or greater");
        }

        if (dto.getSodium() != null && dto.getSodium() < 0) {
            throw new IllegalArgumentException("Sodium must be 0 or greater");
        }

        if (dto.getServingSize() != null && dto.getServingSize() <= 0) {
            throw new IllegalArgumentException("Serving size must be greater than 0");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String buildKey(FoodProductDto dto) {
        if (dto.getBarcode() != null && !dto.getBarcode().isBlank()) {
            return "BARCODE:" + dto.getBarcode().trim();
        }
        return "NAME:" + (dto.getProductName() == null ? "" : dto.getProductName().trim().toLowerCase());
    }
}