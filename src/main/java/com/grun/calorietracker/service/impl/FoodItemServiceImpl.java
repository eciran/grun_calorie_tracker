package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.service.FoodItemService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FoodItemServiceImpl implements FoodItemService {

    private final FoodItemRepository foodItemRepository;

    public FoodItemServiceImpl(FoodItemRepository foodItemRepository) {
        this.foodItemRepository = foodItemRepository;
    }

    @Override
    public FoodItemEntity getOrSaveFoodItemByBarcode(String barcode) {
        Optional<FoodItemEntity> optionalFoodItem = foodItemRepository.findByBarcode(barcode);
        return optionalFoodItem.orElse(null);
    }

    @Override
    public List<FoodProductDto> searchFoodItems(FoodSearchCriteriaDto criteria) {
        return List.of();
    }

}