package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodServingOptionDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionLocalizationEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.FoodServingOptionQualityStatus;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.mapper.FoodServingOptionMapper;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionLocalizationRepository;
import com.grun.calorietracker.service.FoodServingOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FoodServingOptionServiceImpl implements FoodServingOptionService {

    private final FoodItemRepository foodItemRepository;
    private final FoodItemServingOptionRepository foodItemServingOptionRepository;
    private final FoodItemServingOptionLocalizationRepository foodItemServingOptionLocalizationRepository;

    @Override
    @Transactional(readOnly = true)
    public List<FoodServingOptionDto> getServingOptions(Long foodItemId, String email) {
        return getServingOptions(foodItemId, email, PreferredLanguage.EN);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodServingOptionDto> getServingOptions(Long foodItemId, String email, PreferredLanguage language) {
        FoodItemEntity foodItem = foodItemRepository.findById(foodItemId)
                .orElseThrow(() -> new ProductNotFoundException("Food item not found"));
        if (foodItem.getVerificationStatus() == VerificationStatus.REJECTED || !isVisibleToUser(foodItem, email)) {
            throw new ProductNotFoundException("Food item not found");
        }
        var options = foodItemServingOptionRepository.findByFoodItemAndQualityStatusOrderByIsDefaultDescLabelAsc(
                foodItem,
                FoodServingOptionQualityStatus.VERIFIED
        );
        PreferredLanguage resolvedLanguage = language == null ? PreferredLanguage.EN : language;
        Set<PreferredLanguage> languages = resolvedLanguage == PreferredLanguage.EN
                ? Set.of(PreferredLanguage.EN)
                : Set.of(resolvedLanguage, PreferredLanguage.EN);
        Map<Long, Map<PreferredLanguage, FoodItemServingOptionLocalizationEntity>> localizations =
                foodItemServingOptionLocalizationRepository
                        .findByServingOptionIdInAndLanguageInAndActiveTrue(
                                options.stream().map(option -> option.getId()).toList(),
                                languages
                        )
                        .stream()
                        .collect(Collectors.groupingBy(
                                localization -> localization.getServingOption().getId(),
                                Collectors.toMap(
                                        FoodItemServingOptionLocalizationEntity::getLanguage,
                                        Function.identity()
                                )
                        ));
        return options.stream()
                .map(option -> {
                    FoodServingOptionDto dto = FoodServingOptionMapper.toDto(option);
                    Map<PreferredLanguage, FoodItemServingOptionLocalizationEntity> byLanguage =
                            localizations.getOrDefault(option.getId(), Map.of());
                    FoodItemServingOptionLocalizationEntity localization = byLanguage.get(resolvedLanguage);
                    if (localization == null && resolvedLanguage != PreferredLanguage.EN) {
                        localization = byLanguage.get(PreferredLanguage.EN);
                    }
                    if (localization != null && localization.getLabel() != null && !localization.getLabel().isBlank()) {
                        dto.setLabel(localization.getLabel().trim());
                    }
                    return dto;
                })
                .toList();
    }

    private boolean isVisibleToUser(FoodItemEntity product, String email) {
        if (product.getPublicationStatus() == null) {
            return !Boolean.TRUE.equals(product.getIsCustom()) || isOwnedBy(product, email);
        }
        if (product.getPublicationStatus() == com.grun.calorietracker.enums.CatalogPublicationStatus.PUBLISHED) {
            return true;
        }
        if (product.getPublicationStatus() != com.grun.calorietracker.enums.CatalogPublicationStatus.PRIVATE_USER) {
            return false;
        }
        return isOwnedBy(product, email);
    }

    private boolean isOwnedBy(FoodItemEntity product, String email) {
        UserEntity owner = product.getCreatedByUser();
        return owner != null
                && owner.getEmail() != null
                && owner.getEmail().equalsIgnoreCase(email);
    }
}
