package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodServingOptionDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.mapper.FoodServingOptionMapper;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionRepository;
import com.grun.calorietracker.service.FoodServingOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FoodServingOptionServiceImpl implements FoodServingOptionService {

    private final FoodItemRepository foodItemRepository;
    private final FoodItemServingOptionRepository foodItemServingOptionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<FoodServingOptionDto> getServingOptions(Long foodItemId, String email) {
        FoodItemEntity foodItem = foodItemRepository.findById(foodItemId)
                .orElseThrow(() -> new ProductNotFoundException("Food item not found"));
        if (foodItem.getVerificationStatus() == VerificationStatus.REJECTED || !isVisibleToUser(foodItem, email)) {
            throw new ProductNotFoundException("Food item not found");
        }
        return foodItemServingOptionRepository.findByFoodItemOrderByIsDefaultDescLabelAsc(foodItem)
                .stream()
                .map(FoodServingOptionMapper::toDto)
                .toList();
    }

    private boolean isVisibleToUser(FoodItemEntity product, String email) {
        if (!Boolean.TRUE.equals(product.getIsCustom())) {
            return true;
        }
        UserEntity owner = product.getCreatedByUser();
        return owner != null
                && owner.getEmail() != null
                && owner.getEmail().equalsIgnoreCase(email);
    }
}
