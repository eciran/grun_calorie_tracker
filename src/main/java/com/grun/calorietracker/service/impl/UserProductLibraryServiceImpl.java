package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.CustomFoodRequestDto;
import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserFavoriteEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.mapper.FoodItemMapper;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.MealTemplateItemRepository;
import com.grun.calorietracker.repository.UserFavoriteRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.UserProductLibraryService;
import com.grun.calorietracker.service.support.FoodProductQualityRules;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserProductLibraryServiceImpl implements UserProductLibraryService {

    private static final int DEFAULT_LIBRARY_PAGE_SIZE = 50;
    private static final int MAX_LIBRARY_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final FoodItemRepository foodItemRepository;
    private final FoodLogsRepository foodLogsRepository;
    private final MealTemplateItemRepository mealTemplateItemRepository;
    private final UserFavoriteRepository userFavoriteRepository;

    @Override
    @Transactional(readOnly = true)
    public List<FoodProductDto> getRecentProducts(String email, int limit) {
        UserEntity user = getUser(email);
        List<Long> ids = foodLogsRepository.findRecentAvailableFoodItemIds(
                user.getId(),
                VerificationStatus.REJECTED.name(),
                PageRequest.of(0, normalizeLimit(limit))
        );
        Map<Long, FoodItemEntity> productsById = new LinkedHashMap<>();
        foodItemRepository.findAllById(ids).forEach(product -> productsById.put(product.getId(), product));
        return ids.stream()
                .map(productsById::get)
                .filter(product -> product != null)
                .map(FoodItemMapper::mapEntityToDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodProductDto> getFavoriteProducts(String email, int page, int size) {
        UserEntity user = getUser(email);
        return userFavoriteRepository.findAvailableFavorites(
                        user,
                        VerificationStatus.REJECTED,
                        PageRequest.of(safePage(page), safePageSize(size))
                ).stream()
                .map(UserFavoriteEntity::getFoodItem)
                .map(FoodItemMapper::mapEntityToDto)
                .toList();
    }

    @Override
    @Transactional
    public FoodProductDto addFavoriteProduct(String email, Long productId) {
        UserEntity user = getUser(email);
        FoodItemEntity product = getAvailableProduct(productId);
        UserFavoriteEntity favorite = userFavoriteRepository.findByUserAndFoodItem(user, product)
                .orElseGet(() -> {
                    UserFavoriteEntity created = new UserFavoriteEntity();
                    created.setUser(user);
                    created.setFoodItem(product);
                    return userFavoriteRepository.save(created);
                });
        return FoodItemMapper.mapEntityToDto(favorite.getFoodItem());
    }

    @Override
    @Transactional
    public void removeFavoriteProduct(String email, Long productId) {
        UserEntity user = getUser(email);
        FoodItemEntity product = foodItemRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Food item not found"));
        userFavoriteRepository.findByUserAndFoodItem(user, product)
                .ifPresent(userFavoriteRepository::delete);
    }

    @Override
    @Transactional
    public FoodProductDto createCustomFood(String email, CustomFoodRequestDto request) {
        UserEntity user = getUser(email);
        FoodItemEntity product = new FoodItemEntity();
        updateManualNutrition(product, request);
        product.setDataSource(FoodDataSource.MANUAL);
        product.setCatalogType(FoodCatalogType.USER_CUSTOM);
        product.setVerificationStatus(VerificationStatus.VERIFIED);
        product.setImageStatus(ImageStatus.NEEDS_REVIEW);
        product.setMarketRegion(user.getMarketRegion());
        product.setIsCustom(true);
        product.setCreatedByUser(user);
        product.setUsageCount(0L);
        FoodProductQualityRules.updateQualityAndReviewPriority(product);
        return FoodItemMapper.mapEntityToDto(foodItemRepository.save(product));
    }

    @Override
    @Transactional
    public FoodProductDto updateCustomFood(String email, Long productId, CustomFoodRequestDto request) {
        FoodItemEntity product = getOwnedCustomFood(getUser(email), productId);
        updateManualNutrition(product, request);
        FoodProductQualityRules.updateQualityAndReviewPriority(product);
        return FoodItemMapper.mapEntityToDto(foodItemRepository.save(product));
    }

    @Override
    @Transactional
    public void deleteCustomFood(String email, Long productId) {
        FoodItemEntity product = getOwnedCustomFood(getUser(email), productId);
        if (foodLogsRepository.existsByFoodItem(product) || mealTemplateItemRepository.existsByFoodItem(product)) {
            throw new IllegalArgumentException("Custom food used by diary history or a saved meal template cannot be deleted");
        }
        userFavoriteRepository.deleteByFoodItem(product);
        foodItemRepository.delete(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodProductDto> getCustomFoods(String email, int page, int size) {
        UserEntity user = getUser(email);
        return foodItemRepository.findByCreatedByUserAndIsCustomTrueOrderByNameAsc(
                        user,
                        PageRequest.of(safePage(page), safePageSize(size))
                ).stream()
                .map(FoodItemMapper::mapEntityToDto)
                .toList();
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private FoodItemEntity getAvailableProduct(Long productId) {
        FoodItemEntity product = foodItemRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Food item not found"));
        if (product.getVerificationStatus() == VerificationStatus.REJECTED) {
            throw new ProductNotFoundException("Food item is not available");
        }
        return product;
    }

    private FoodItemEntity getOwnedCustomFood(UserEntity user, Long productId) {
        FoodItemEntity product = foodItemRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Custom food item not found"));
        if (!Boolean.TRUE.equals(product.getIsCustom())
                || product.getCreatedByUser() == null
                || !user.getId().equals(product.getCreatedByUser().getId())) {
            throw new ProductNotFoundException("Custom food item not found");
        }
        return product;
    }

    private void updateManualNutrition(FoodItemEntity product, CustomFoodRequestDto request) {
        product.setName(request.getName().trim());
        product.setCalories(request.getCalories());
        product.setProtein(request.getProtein());
        product.setFat(request.getFat());
        product.setCarbs(request.getCarbs());
        product.setServingSizeGrams(request.getServingSizeGrams());
        product.setServingUnit(trimToNull(request.getServingUnit()));
    }

    private int normalizeLimit(int limit) {
        if (limit < 1) {
            return 10;
        }
        return Math.min(limit, 50);
    }

    private int safePage(int page) {
        return Math.max(page, 0);
    }

    private int safePageSize(int size) {
        if (size < 1) {
            return DEFAULT_LIBRARY_PAGE_SIZE;
        }
        return Math.min(size, MAX_LIBRARY_PAGE_SIZE);
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
