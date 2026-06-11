package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.ProductCorrectionSuggestionDto;
import com.grun.calorietracker.dto.ProductCorrectionSuggestionRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.ProductCorrectionSuggestionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.ProductCorrectionStatus;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.ProductCorrectionSuggestionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.ProductCorrectionSuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductCorrectionSuggestionServiceImpl implements ProductCorrectionSuggestionService {

    private final ProductCorrectionSuggestionRepository productCorrectionSuggestionRepository;
    private final FoodItemRepository foodItemRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ProductCorrectionSuggestionDto suggestCorrection(Long foodItemId, String email, ProductCorrectionSuggestionRequestDto request) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        FoodItemEntity foodItem = foodItemRepository.findById(foodItemId)
                .orElseThrow(() -> new ProductNotFoundException("Food item not found"));
        if (foodItem.getVerificationStatus() == VerificationStatus.REJECTED) {
            throw new ProductNotFoundException("Food item not found");
        }
        ProductCorrectionSuggestionEntity entity = new ProductCorrectionSuggestionEntity();
        entity.setFoodItem(foodItem);
        entity.setUser(user);
        entity.setSuggestedCalories(request.getSuggestedCalories());
        entity.setSuggestedProtein(request.getSuggestedProtein());
        entity.setSuggestedCarbs(request.getSuggestedCarbs());
        entity.setSuggestedFat(request.getSuggestedFat());
        entity.setNote(trimToNull(request.getNote()));
        entity.setImageUrl(trimToNull(request.getImageUrl()));
        entity.setStatus(ProductCorrectionStatus.OPEN);
        entity.setCreatedAt(LocalDateTime.now());
        return toDto(productCorrectionSuggestionRepository.save(entity));
    }

    private ProductCorrectionSuggestionDto toDto(ProductCorrectionSuggestionEntity entity) {
        ProductCorrectionSuggestionDto dto = new ProductCorrectionSuggestionDto();
        dto.setId(entity.getId());
        dto.setFoodItemId(entity.getFoodItem().getId());
        dto.setSuggestedCalories(entity.getSuggestedCalories());
        dto.setSuggestedProtein(entity.getSuggestedProtein());
        dto.setSuggestedCarbs(entity.getSuggestedCarbs());
        dto.setSuggestedFat(entity.getSuggestedFat());
        dto.setNote(entity.getNote());
        dto.setImageUrl(entity.getImageUrl());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
