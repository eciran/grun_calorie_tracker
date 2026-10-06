package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminFoodServingOptionUpsertRequestDto;
import com.grun.calorietracker.dto.AdminProductQualityWorkbenchDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionLocalizationEntity;
import com.grun.calorietracker.entity.FoodProductReviewAuditEntity;
import com.grun.calorietracker.enums.FoodProductReviewAuditAction;
import com.grun.calorietracker.enums.FoodServingOptionQualityStatus;
import com.grun.calorietracker.enums.FoodServingOptionSource;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionLocalizationRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionRepository;
import com.grun.calorietracker.repository.FoodProductReviewAuditRepository;
import com.grun.calorietracker.service.AdminFoodServingOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminFoodServingOptionServiceImpl implements AdminFoodServingOptionService {

    private static final double EPSILON = 0.000001;
    private static final Set<PreferredLanguage> REQUIRED_LANGUAGES =
            Set.of(PreferredLanguage.EN, PreferredLanguage.TR);

    private final FoodItemRepository foodItemRepository;
    private final FoodItemServingOptionRepository servingOptionRepository;
    private final FoodItemServingOptionLocalizationRepository localizationRepository;
    private final FoodProductReviewAuditRepository auditRepository;

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"foodProductById", "foodProductByBarcode", "foodProductSearch"}, allEntries = true)
    public AdminProductQualityWorkbenchDto.ServingOptionDto create(
            Long productId,
            AdminFoodServingOptionUpsertRequestDto request,
            String reviewedBy
    ) {
        FoodItemEntity product = findProduct(productId);
        validate(request);
        ensureUniqueConversion(product, null, request);

        FoodItemServingOptionEntity option = new FoodItemServingOptionEntity();
        option.setFoodItem(product);
        apply(option, request);
        clearPreviousDefault(product, option);
        option = servingOptionRepository.save(option);
        List<FoodItemServingOptionLocalizationEntity> localizations = syncLocalizations(option, request);
        writeAudit(product, reviewedBy, "servingOptions", null, snapshot(option), "Manual serving option created.");
        return toDto(option, localizations);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"foodProductById", "foodProductByBarcode", "foodProductSearch"}, allEntries = true)
    public AdminProductQualityWorkbenchDto.ServingOptionDto update(
            Long productId,
            Long optionId,
            AdminFoodServingOptionUpsertRequestDto request,
            String reviewedBy
    ) {
        FoodItemEntity product = findProduct(productId);
        FoodItemServingOptionEntity option = servingOptionRepository.findByIdAndFoodItem(optionId, product)
                .orElseThrow(() -> new ResourceNotFoundException("Serving option not found: " + optionId));
        validate(request);
        ensureUniqueConversion(product, optionId, request);
        preventRemovingOnlyDefault(product, option, request);

        String oldValue = snapshot(option);
        apply(option, request);
        clearPreviousDefault(product, option);
        option = servingOptionRepository.save(option);
        List<FoodItemServingOptionLocalizationEntity> localizations = syncLocalizations(option, request);
        writeAudit(product, reviewedBy, "servingOptions." + optionId, oldValue, snapshot(option),
                "Manual serving option updated.");
        return toDto(option, localizations);
    }

    private FoodItemEntity findProduct(Long productId) {
        return foodItemRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Food product not found: " + productId));
    }

    private void validate(AdminFoodServingOptionUpsertRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Serving option request must not be empty.");
        }
        if (request.getLabel() == null || request.getLabel().isBlank()) {
            throw new IllegalArgumentException("Serving option label is required.");
        }
        if (request.getUnitType() == null) {
            throw new IllegalArgumentException("Serving option unit type is required.");
        }
        if (!isPositive(request.getQuantity())) {
            throw new IllegalArgumentException("Serving option quantity must be positive.");
        }
        if (!isPositive(request.getGramWeight()) && !isPositive(request.getMlVolume())) {
            throw new IllegalArgumentException("A positive gram weight or milliliter volume is required.");
        }
        FoodServingOptionQualityStatus status = request.getQualityStatus() == null
                ? FoodServingOptionQualityStatus.VERIFIED
                : request.getQualityStatus();
        boolean visible = !Boolean.FALSE.equals(request.getUserVisible());
        boolean defaultOption = Boolean.TRUE.equals(request.getDefaultOption());
        if (visible && status != FoodServingOptionQualityStatus.VERIFIED) {
            throw new IllegalArgumentException("A user-visible serving option must be VERIFIED.");
        }
        if (defaultOption && (!visible || status != FoodServingOptionQualityStatus.VERIFIED)) {
            throw new IllegalArgumentException("The default serving option must be visible and VERIFIED.");
        }

        Map<PreferredLanguage, String> labels = localizationLabels(request);
        if (!labels.keySet().equals(REQUIRED_LANGUAGES)) {
            throw new IllegalArgumentException("Exactly one EN and one TR serving label are required.");
        }
    }

    private Map<PreferredLanguage, String> localizationLabels(AdminFoodServingOptionUpsertRequestDto request) {
        Map<PreferredLanguage, String> labels = new EnumMap<>(PreferredLanguage.class);
        if (request.getLocalizations() == null) {
            return labels;
        }
        for (AdminFoodServingOptionUpsertRequestDto.Localization localization : request.getLocalizations()) {
            if (localization == null || localization.language() == null
                    || localization.label() == null || localization.label().isBlank()) {
                throw new IllegalArgumentException("Serving option localization language and label are required.");
            }
            if (labels.put(localization.language(), localization.label().trim()) != null) {
                throw new IllegalArgumentException("Serving option localization languages must be unique.");
            }
        }
        return labels;
    }

    private void ensureUniqueConversion(
            FoodItemEntity product,
            Long currentOptionId,
            AdminFoodServingOptionUpsertRequestDto request
    ) {
        boolean duplicate = servingOptionRepository.findByFoodItemOrderByIsDefaultDescLabelAsc(product).stream()
                .filter(existing -> currentOptionId == null || !currentOptionId.equals(existing.getId()))
                .anyMatch(existing -> existing.getUnitType() == request.getUnitType()
                        && same(existing.getQuantity(), request.getQuantity())
                        && same(existing.getGramWeight(), request.getGramWeight())
                        && same(existing.getMlVolume(), request.getMlVolume()));
        if (duplicate) {
            throw new IllegalArgumentException("An equivalent serving option already exists for this product.");
        }
    }

    private void preventRemovingOnlyDefault(
            FoodItemEntity product,
            FoodItemServingOptionEntity option,
            AdminFoodServingOptionUpsertRequestDto request
    ) {
        if (!Boolean.TRUE.equals(option.getIsDefault()) || Boolean.TRUE.equals(request.getDefaultOption())) {
            return;
        }
        boolean hasAnotherDefault = servingOptionRepository.findByFoodItemOrderByIsDefaultDescLabelAsc(product).stream()
                .anyMatch(existing -> !Objects.equals(existing.getId(), option.getId())
                        && Boolean.TRUE.equals(existing.getIsDefault()));
        if (!hasAnotherDefault) {
            throw new IllegalArgumentException("Select another default option before removing the current default.");
        }
    }

    private void apply(FoodItemServingOptionEntity option, AdminFoodServingOptionUpsertRequestDto request) {
        option.setLabel(request.getLabel().trim());
        option.setUnitType(request.getUnitType());
        option.setQuantity(request.getQuantity());
        option.setGramWeight(request.getGramWeight());
        option.setMlVolume(request.getMlVolume());
        option.setIsDefault(Boolean.TRUE.equals(request.getDefaultOption()));
        option.setUserVisible(!Boolean.FALSE.equals(request.getUserVisible()));
        option.setSource(FoodServingOptionSource.ADMIN);
        option.setQualityStatus(request.getQualityStatus() == null
                ? FoodServingOptionQualityStatus.VERIFIED
                : request.getQualityStatus());
    }

    private void clearPreviousDefault(FoodItemEntity product, FoodItemServingOptionEntity selected) {
        if (!Boolean.TRUE.equals(selected.getIsDefault())) {
            return;
        }
        List<FoodItemServingOptionEntity> changed = servingOptionRepository
                .findByFoodItemOrderByIsDefaultDescLabelAsc(product).stream()
                .filter(existing -> !Objects.equals(existing.getId(), selected.getId()))
                .filter(existing -> Boolean.TRUE.equals(existing.getIsDefault()))
                .peek(existing -> existing.setIsDefault(false))
                .toList();
        if (!changed.isEmpty()) {
            servingOptionRepository.saveAll(changed);
        }
    }

    private List<FoodItemServingOptionLocalizationEntity> syncLocalizations(
            FoodItemServingOptionEntity option,
            AdminFoodServingOptionUpsertRequestDto request
    ) {
        Map<PreferredLanguage, String> labels = localizationLabels(request);
        Map<PreferredLanguage, FoodItemServingOptionLocalizationEntity> existing = new EnumMap<>(PreferredLanguage.class);
        localizationRepository.findByServingOptionIdIn(List.of(option.getId()))
                .forEach(value -> existing.put(value.getLanguage(), value));
        LocalDateTime now = LocalDateTime.now();
        List<FoodItemServingOptionLocalizationEntity> values = labels.entrySet().stream().map(entry -> {
            FoodItemServingOptionLocalizationEntity localization = existing.getOrDefault(
                    entry.getKey(), new FoodItemServingOptionLocalizationEntity());
            localization.setServingOption(option);
            localization.setLanguage(entry.getKey());
            localization.setLabel(entry.getValue());
            localization.setSource("admin-manual");
            localization.setActive(true);
            if (localization.getCreatedAt() == null) {
                localization.setCreatedAt(now);
            }
            localization.setUpdatedAt(now);
            return localization;
        }).toList();
        return localizationRepository.saveAll(values);
    }

    private void writeAudit(
            FoodItemEntity product,
            String reviewedBy,
            String fieldName,
            String oldValue,
            String newValue,
            String note
    ) {
        FoodProductReviewAuditEntity audit = new FoodProductReviewAuditEntity();
        audit.setFoodItem(product);
        audit.setReviewedBy(reviewedBy);
        audit.setActionType(FoodProductReviewAuditAction.SERVING_OPTION_CHANGE);
        audit.setFieldName(fieldName);
        audit.setOldValue(oldValue);
        audit.setNewValue(newValue);
        audit.setNote(note);
        auditRepository.save(audit);
    }

    private AdminProductQualityWorkbenchDto.ServingOptionDto toDto(
            FoodItemServingOptionEntity option,
            List<FoodItemServingOptionLocalizationEntity> localizations
    ) {
        return new AdminProductQualityWorkbenchDto.ServingOptionDto(
                option.getId(), option.getLabel(), option.getUnitType(), option.getQuantity(),
                option.getGramWeight(), option.getMlVolume(), option.getIsDefault(), option.getSource(),
                option.getQualityStatus(), option.getUserVisible(), localizations.stream()
                .map(value -> new AdminProductQualityWorkbenchDto.ServingLocalizationDto(
                        value.getId(), value.getLanguage(), value.getLabel(), value.getSource(), value.getActive()))
                .toList()
        );
    }

    private String snapshot(FoodItemServingOptionEntity option) {
        return "%s|%s|qty=%s|g=%s|ml=%s|default=%s|visible=%s|quality=%s".formatted(
                option.getLabel(), option.getUnitType(), option.getQuantity(), option.getGramWeight(),
                option.getMlVolume(), option.getIsDefault(), option.getUserVisible(), option.getQualityStatus());
    }

    private boolean isPositive(Double value) {
        return value != null && Double.isFinite(value) && value > 0;
    }

    private boolean same(Double first, Double second) {
        if (first == null || second == null) {
            return first == null && second == null;
        }
        return Math.abs(first - second) < EPSILON;
    }
}
