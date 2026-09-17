package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.CustomFoodRequestDto;
import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemLocalizationEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserFavoriteEntity;
import com.grun.calorietracker.enums.CatalogPublicationStatus;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.FoodNutritionBasis;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.mapper.FoodItemMapper;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemLocalizationRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.MealTemplateItemRepository;
import com.grun.calorietracker.repository.UserFavoriteRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.UserProductLibraryService;
import com.grun.calorietracker.service.support.FoodProductQualityRules;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserProductLibraryServiceImpl implements UserProductLibraryService {

    private static final int DEFAULT_LIBRARY_PAGE_SIZE = 50;
    private static final int MAX_LIBRARY_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final FoodItemRepository foodItemRepository;
    private final FoodItemLocalizationRepository foodItemLocalizationRepository;
    private final FoodLogsRepository foodLogsRepository;
    private final MealTemplateItemRepository mealTemplateItemRepository;
    private final UserFavoriteRepository userFavoriteRepository;

    @Override
    @Transactional(readOnly = true)
    public List<FoodProductDto> getRecentProducts(String email, int limit, PreferredLanguage requestedLanguage) {
        UserEntity user = getUser(email);
        PreferredLanguage language = resolveLanguage(requestedLanguage, user);
        List<Long> ids = foodLogsRepository.findRecentAvailableFoodItemIds(
                user.getId(),
                VerificationStatus.REJECTED.name(),
                user.getRecentProductsClearedAt(),
                PageRequest.of(0, normalizeLimit(limit))
        );
        Map<Long, FoodItemEntity> productsById = new LinkedHashMap<>();
        foodItemRepository.findAllById(ids).forEach(product -> productsById.put(product.getId(), product));
        Map<Long, Map<PreferredLanguage, FoodItemLocalizationEntity>> localizations = loadLocalizations(ids, language);
        return ids.stream()
                .map(productsById::get)
                .filter(product -> product != null)
                .filter(product -> product.getVerificationStatus() != VerificationStatus.REJECTED)
                .filter(product -> isVisibleToUser(product, user))
                .map(product -> toLocalizedDto(product, language, localizations))
                .toList();
    }

    @Override
    @Transactional
    public void clearRecentProducts(String email) {
        UserEntity user = getUser(email);
        user.setRecentProductsClearedAt(java.time.LocalDateTime.now());
        userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodProductDto> getFavoriteProducts(String email, int page, int size) {
        UserEntity user = getUser(email);
        PreferredLanguage language = resolveLanguage(null, user);
        List<FoodItemEntity> products = userFavoriteRepository.findAvailableFavorites(
                        user,
                        VerificationStatus.REJECTED,
                        PageRequest.of(safePage(page), safePageSize(size))
                ).stream()
                .map(UserFavoriteEntity::getFoodItem)
                .filter(product -> product.getVerificationStatus() != VerificationStatus.REJECTED)
                .filter(product -> isVisibleToUser(product, user))
                .toList();
        Map<Long, Map<PreferredLanguage, FoodItemLocalizationEntity>> localizations = loadLocalizations(
                products.stream().map(FoodItemEntity::getId).toList(), language);
        return products.stream().map(product -> toLocalizedDto(product, language, localizations)).toList();
    }

    @Override
    @Transactional
    public FoodProductDto addFavoriteProduct(String email, Long productId) {
        UserEntity user = getUser(email);
        FoodItemEntity product = getAvailableProduct(productId, user);
        UserFavoriteEntity favorite = userFavoriteRepository.findByUserAndFoodItem(user, product)
                .orElseGet(() -> {
                    UserFavoriteEntity created = new UserFavoriteEntity();
                    created.setUser(user);
                    created.setFoodItem(product);
                    return userFavoriteRepository.save(created);
                });
        return toLocalizedDto(favorite.getFoodItem(), resolveLanguage(null, user), null);
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
    @CacheEvict(cacheNames = {"foodProductById", "foodProductSearch"}, allEntries = true)
    public FoodProductDto createCustomFood(String email, CustomFoodRequestDto request) {
        UserEntity user = getUser(email);
        FoodItemEntity product = new FoodItemEntity();
        updateManualNutrition(product, request);
        product.setDataSource(FoodDataSource.MANUAL);
        product.setCatalogType(FoodCatalogType.USER_CUSTOM);
        product.setVerificationStatus(VerificationStatus.VERIFIED);
        product.setPublicationStatus(CatalogPublicationStatus.PRIVATE_USER);
        product.setImageStatus(ImageStatus.NEEDS_REVIEW);
        product.setNutritionBasis(FoodNutritionBasis.ESTIMATED);

        product.setMarketRegion(user.getMarketRegion());
        product.setIsCustom(true);
        product.setCreatedByUser(user);
        product.setUsageCount(0L);
        FoodProductQualityRules.updateQualityAndReviewPriority(product);
        return FoodItemMapper.mapEntityToDto(foodItemRepository.save(product));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"foodProductById", "foodProductSearch"}, allEntries = true)
    public FoodProductDto updateCustomFood(String email, Long productId, CustomFoodRequestDto request) {
        FoodItemEntity product = getOwnedCustomFood(getUser(email), productId);
        updateManualNutrition(product, request);
        FoodProductQualityRules.updateQualityAndReviewPriority(product);
        return FoodItemMapper.mapEntityToDto(foodItemRepository.save(product));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"foodProductById", "foodProductSearch"}, allEntries = true)
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
                .filter(product -> isVisibleToUser(product, user))
                .map(FoodItemMapper::mapEntityToDto)
                .toList();
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private FoodItemEntity getAvailableProduct(Long productId, UserEntity user) {
        FoodItemEntity product = foodItemRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Food item not found"));
        if (product.getVerificationStatus() == VerificationStatus.REJECTED || !isVisibleToUser(product, user)) {
            throw new ProductNotFoundException("Food item is not available");
        }
        return product;
    }

    private boolean isVisibleToUser(FoodItemEntity product, UserEntity user) {
        if (product.getPublicationStatus() == null) {
            return !Boolean.TRUE.equals(product.getIsCustom()) || isOwnedBy(product, user);
        }
        if (product.getPublicationStatus() == CatalogPublicationStatus.PUBLISHED) {
            return true;
        }
        if (product.getPublicationStatus() != CatalogPublicationStatus.PRIVATE_USER) {
            return false;
        }
        return isOwnedBy(product, user);
    }

    private boolean isOwnedBy(FoodItemEntity product, UserEntity user) {
        return product.getCreatedByUser() != null
                && user.getId() != null
                && user.getId().equals(product.getCreatedByUser().getId());
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
        product.setBrand(trimToNull(request.getBrand()));
        product.setCalories(request.getCalories());
        product.setProtein(request.getProtein());
        product.setFat(request.getFat());
        product.setCarbs(request.getCarbs());
        product.setFiber(request.getFiber());
        product.setSugar(request.getSugar());
        product.setSodium(request.getSodium());
        product.setNutritionReferenceUnit(request.getNutritionReferenceUnit() != null ? request.getNutritionReferenceUnit() : product.getNutritionReferenceUnit() != null ? product.getNutritionReferenceUnit() : com.grun.calorietracker.enums.FoodNutritionReferenceUnit.PER_100G);
        product.setSaturatedFat(request.getSaturatedFat());
        product.setTransFat(request.getTransFat());
        product.setCholesterol(request.getCholesterol());
        product.setPotassium(request.getPotassium());
        product.setCalcium(request.getCalcium());
        product.setIron(request.getIron());
        product.setMagnesium(request.getMagnesium());
        product.setZinc(request.getZinc());
        product.setVitaminA(request.getVitaminA());
        product.setVitaminC(request.getVitaminC());
        product.setVitaminD(request.getVitaminD());
        product.setVitaminE(request.getVitaminE());
        product.setVitaminB12(request.getVitaminB12());
        product.setServingSizeGrams(product.getNutritionReferenceUnit() == com.grun.calorietracker.enums.FoodNutritionReferenceUnit.PER_100ML ? null : request.getServingSizeGrams());
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

    private PreferredLanguage resolveLanguage(PreferredLanguage requestedLanguage, UserEntity user) {
        if (requestedLanguage != null) {
            return requestedLanguage;
        }
        return user != null && user.getPreferredLanguage() != null ? user.getPreferredLanguage() : PreferredLanguage.EN;
    }

    private Map<Long, Map<PreferredLanguage, FoodItemLocalizationEntity>> loadLocalizations(
            List<Long> productIds,
            PreferredLanguage language
    ) {
        Map<Long, Map<PreferredLanguage, FoodItemLocalizationEntity>> result = new HashMap<>();
        if (productIds == null || productIds.isEmpty()) {
            return result;
        }
        Set<PreferredLanguage> languages = language == PreferredLanguage.EN
                ? Set.of(PreferredLanguage.EN)
                : Set.of(language, PreferredLanguage.EN);
        foodItemLocalizationRepository.findByFoodItemIdInAndLanguageInAndActiveTrue(productIds, languages)
                .forEach(localization -> result
                        .computeIfAbsent(localization.getFoodItem().getId(), ignored -> new HashMap<>())
                        .put(localization.getLanguage(), localization));
        return result;
    }

    private FoodProductDto toLocalizedDto(
            FoodItemEntity product,
            PreferredLanguage language,
            Map<Long, Map<PreferredLanguage, FoodItemLocalizationEntity>> localizations
    ) {
        FoodProductDto dto = FoodItemMapper.mapEntityToDto(product);
        FoodItemLocalizationEntity localization = null;
        if (localizations != null) {
            Map<PreferredLanguage, FoodItemLocalizationEntity> byLanguage = localizations.get(product.getId());
            if (byLanguage != null) {
                localization = byLanguage.get(language);
                if (localization == null) localization = byLanguage.get(PreferredLanguage.EN);
            }
        } else {
            localization = foodItemLocalizationRepository
                    .findByFoodItemIdAndLanguageAndActiveTrue(product.getId(), language)
                    .or(() -> language == PreferredLanguage.EN
                            ? java.util.Optional.empty()
                            : foodItemLocalizationRepository.findByFoodItemIdAndLanguageAndActiveTrue(
                                    product.getId(), PreferredLanguage.EN))
                    .orElse(null);
        }
        dto.setLanguage(language);
        if (localization == null) {
            return dto;
        }
        String displayName = FoodProductNormalizationRules.normalizeProductDisplayName(localization.getDisplayName());
        String shortDisplayName = FoodProductNormalizationRules.normalizeProductDisplayName(localization.getShortDisplayName());
        if (displayName != null) dto.setDisplayName(displayName);
        if (shortDisplayName != null) dto.setShortDisplayName(shortDisplayName);
        else if (displayName != null) dto.setShortDisplayName(displayName);
        boolean explicitPreparation = product.getPreparationState() != null
                && product.getPreparationState() != FoodPreparationState.UNSPECIFIED;
        String productName = explicitPreparation ? dto.getDisplayName() : dto.getShortDisplayName();
        dto.setProductName(productName != null ? productName : dto.getDisplayName());
        return dto;
    }
}
