package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.service.OpenFoodFactsService;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class OpenFoodFactsServiceImpl implements OpenFoodFactsService {
    @Override
    public List<FoodProductDto> searchProducts(String query) {
        return List.of();
    }

    @Override
    public Optional<FoodProductDto> getProductByBarcode(String barcode) {
        return Optional.empty();
    }

    @Override
    public List<FoodProductDto> searchProductsByCriteria(FoodSearchCriteriaDto criteria) {
        return List.of();
    }
}