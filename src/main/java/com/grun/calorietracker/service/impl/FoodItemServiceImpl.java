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
        String nutriScore = normalize(criteria.getNutriScore());

        List<FoodProductDto> localResults = FoodItemMapper.mapEntityListToDtoList(
                foodItemRepository.searchFoodItems(
                        query,
                        criteria.getMinCalories(),
                        criteria.getMaxCalories(),
                        nutriScore
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