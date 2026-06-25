package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.MealTemplateRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.MealTemplateService;
import com.grun.calorietracker.service.support.FoodPortionCalculator;
import com.grun.calorietracker.service.support.FoodProductQualityRules;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MealTemplateServiceImpl implements MealTemplateService {

    private static final int DEFAULT_TEMPLATE_PAGE_SIZE = 50;
    private static final int MAX_TEMPLATE_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final FoodLogsRepository foodLogsRepository;
    private final FoodItemRepository foodItemRepository;
    private final MealTemplateRepository mealTemplateRepository;

    @Override
    @Transactional
    public MealTemplateDto createFromLoggedMeal(String email, MealTemplateCreateRequestDto request) {
        UserEntity user = getUser(email);
        String mealType = normalizeMealType(request.getMealType());

        MealTemplateEntity template = new MealTemplateEntity();
        template.setUser(user);
        template.setName(request.getName().trim());
        template.setMealType(mealType);

        List<MealTemplateItemEntity> items = new ArrayList<>();
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            for (int index = 0; index < request.getItems().size(); index++) {
                items.add(toTemplateItem(template, request.getItems().get(index), user, index));
            }
        } else {
            if (request.getSourceDate() == null) {
                throw new IllegalArgumentException("sourceDate is required when items is empty");
            }
            List<FoodLogsEntity> sourceLogs = foodLogsRepository.findByUserAndMealTypeAndLogDateBetween(
                    user,
                    mealType,
                    request.getSourceDate().atStartOfDay(),
                    request.getSourceDate().plusDays(1).atStartOfDay()
            );
            if (sourceLogs.isEmpty()) {
                throw new IllegalArgumentException("Source meal has no diary entries");
            }
            for (int index = 0; index < sourceLogs.size(); index++) {
                FoodLogsEntity source = sourceLogs.get(index);
                ensureFoodAvailable(source.getFoodItem(), user);
                MealTemplateItemEntity item = new MealTemplateItemEntity();
                item.setTemplate(template);
                item.setFoodItem(source.getFoodItem());
                item.setPortionSize(source.getPortionSize());
                item.setPortionUnit(FoodPortionCalculator.resolveUnit(source.getPortionUnit()));
                item.setNormalizedPortionGrams(source.getNormalizedPortionGrams());
                item.setLogTime(source.getLogDate() == null ? null : source.getLogDate().toLocalTime());
                item.setItemOrder(index);
                items.add(item);
            }
        }

        template.setItems(items);
        return toDto(mealTemplateRepository.save(template));
    }
    @Override
    @Transactional(readOnly = true)
    public List<MealTemplateDto> getTemplates(String email, int page, int size) {
        return mealTemplateRepository.findByUserOrderByCreatedAtDesc(
                        getUser(email),
                        PageRequest.of(safePage(page), safePageSize(size))
                ).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public MealTemplateDto updateTemplate(String email, Long templateId, MealTemplateUpdateRequestDto request) {
        UserEntity user = getUser(email);
        MealTemplateEntity template = mealTemplateRepository.findByIdAndUser(templateId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Meal template not found"));
        template.setName(request.getName().trim());
        template.setMealType(normalizeMealType(request.getMealType()));
        template.getItems().clear();
        for (int index = 0; index < request.getItems().size(); index++) {
            template.getItems().add(toTemplateItem(template, request.getItems().get(index), user, index));
        }
        return toDto(mealTemplateRepository.save(template));
    }

    @Override
    @Transactional
    public List<FoodLogsDto> applyTemplate(String email, Long templateId, MealTemplateApplyRequestDto request) {
        UserEntity user = getUser(email);
        MealTemplateEntity template = mealTemplateRepository.findByIdAndUser(templateId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Meal template not found"));
        String targetMealType = request.getMealType() == null || request.getMealType().isBlank()
                ? template.getMealType()
                : normalizeMealType(request.getMealType());
        return template.getItems().stream()
                .map(item -> toFoodLog(item, user, request, targetMealType))
                .map(foodLogsRepository::save)
                .peek(saved -> markFoodItemUsed(saved.getFoodItem()))
                .map(this::toFoodLogDto)
                .toList();
    }

    @Override
    @Transactional
    public void deleteTemplate(String email, Long templateId) {
        MealTemplateEntity template = mealTemplateRepository.findByIdAndUser(templateId, getUser(email))
                .orElseThrow(() -> new ResourceNotFoundException("Meal template not found"));
        mealTemplateRepository.delete(template);
    }

    private FoodLogsEntity toFoodLog(MealTemplateItemEntity item,
                                     UserEntity user,
                                     MealTemplateApplyRequestDto request,
                                     String mealType) {
        ensureFoodAvailable(item.getFoodItem(), user);
        FoodLogsEntity log = new FoodLogsEntity();
        log.setUser(user);
        log.setFoodItem(item.getFoodItem());
        log.setPortionSize(item.getPortionSize());
        log.setPortionUnit(FoodPortionCalculator.resolveUnit(item.getPortionUnit()));
        log.setNormalizedPortionGrams(item.getNormalizedPortionGrams());
        applyNutritionSnapshot(log, item.getFoodItem());
        log.setMealType(mealType);
        log.setLogDate(request.getTargetDate().atTime(item.getLogTime() == null ? LocalTime.NOON : item.getLogTime()));
        return log;
    }

    private MealTemplateDto toDto(MealTemplateEntity template) {
        MealTemplateDto dto = new MealTemplateDto();
        dto.setId(template.getId());
        dto.setName(template.getName());
        dto.setMealType(template.getMealType());
        dto.setCreatedAt(template.getCreatedAt());
        List<MealTemplateItemDto> items = template.getItems().stream().map(this::toItemDto).toList();
        dto.setItems(items);
        dto.setTotalCalories(round(items.stream().mapToDouble(item -> safe(item.getCalories())).sum()));
        dto.setTotalProtein(round(items.stream().mapToDouble(item -> safe(item.getProtein())).sum()));
        dto.setTotalCarbs(round(items.stream().mapToDouble(item -> safe(item.getCarbs())).sum()));
        dto.setTotalFat(round(items.stream().mapToDouble(item -> safe(item.getFat())).sum()));
        return dto;
    }

    private MealTemplateItemDto toItemDto(MealTemplateItemEntity item) {
        MealTemplateItemDto dto = new MealTemplateItemDto();
        FoodItemEntity foodItem = item.getFoodItem();
        Double grams = item.getNormalizedPortionGrams() != null
                ? item.getNormalizedPortionGrams()
                : item.getPortionSize();
        dto.setFoodItemId(foodItem.getId());
        dto.setFoodName(foodItem.getName());
        dto.setPortionSize(item.getPortionSize());
        dto.setPortionUnit(FoodPortionCalculator.resolveUnit(item.getPortionUnit()));
        dto.setNormalizedPortionGrams(item.getNormalizedPortionGrams());
        dto.setCalories(calculateNutritionValue(foodItem.getCalories(), grams));
        dto.setProtein(calculateNutritionValue(foodItem.getProtein(), grams));
        dto.setCarbs(calculateNutritionValue(foodItem.getCarbs(), grams));
        dto.setFat(calculateNutritionValue(foodItem.getFat(), grams));
        return dto;
    }
    private MealTemplateItemEntity toTemplateItem(MealTemplateEntity template,
                                                  MealTemplateItemRequestDto request,
                                                  UserEntity user,
                                                  int itemOrder) {
        FoodItemEntity foodItem = foodItemRepository.findById(request.getFoodItemId())
                .orElseThrow(() -> new ProductNotFoundException("Food item not found"));
        ensureFoodAvailable(foodItem, user);
        MealTemplateItemEntity item = new MealTemplateItemEntity();
        item.setTemplate(template);
        item.setFoodItem(foodItem);
        item.setPortionSize(request.getPortionSize());
        item.setPortionUnit(FoodPortionCalculator.resolveUnit(request.getPortionUnit()));
        item.setNormalizedPortionGrams(FoodPortionCalculator.normalizeToGrams(
                request.getPortionSize(),
                item.getPortionUnit(),
                foodItem
        ));
        item.setLogTime(LocalTime.NOON);
        item.setItemOrder(itemOrder);
        return item;
    }

    private FoodLogsDto toFoodLogDto(FoodLogsEntity log) {
        FoodLogsDto dto = new FoodLogsDto();
        dto.setId(log.getId());
        dto.setFoodItemId(log.getFoodItem().getId());
        dto.setFoodName(log.getFoodItem().getName());
        dto.setPortionSize(log.getPortionSize());
        dto.setPortionUnit(FoodPortionCalculator.resolveUnit(log.getPortionUnit()));
        dto.setNormalizedPortionGrams(log.getNormalizedPortionGrams());
        dto.setSnapshotCalories(log.getSnapshotCalories());
        dto.setSnapshotProtein(log.getSnapshotProtein());
        dto.setSnapshotCarbs(log.getSnapshotCarbs());
        dto.setSnapshotFat(log.getSnapshotFat());
        dto.setSnapshotFiber(log.getSnapshotFiber());
        dto.setSnapshotSugar(log.getSnapshotSugar());
        dto.setSnapshotSaturatedFat(log.getSnapshotSaturatedFat());
        dto.setSnapshotSodium(log.getSnapshotSodium());
        dto.setSnapshotPotassium(log.getSnapshotPotassium());
        dto.setSnapshotCholesterol(log.getSnapshotCholesterol());
        dto.setSnapshotCalcium(log.getSnapshotCalcium());
        dto.setSnapshotIron(log.getSnapshotIron());
        dto.setSnapshotMagnesium(log.getSnapshotMagnesium());
        dto.setSnapshotZinc(log.getSnapshotZinc());
        dto.setSnapshotVitaminA(log.getSnapshotVitaminA());
        dto.setSnapshotVitaminC(log.getSnapshotVitaminC());
        dto.setSnapshotVitaminD(log.getSnapshotVitaminD());
        dto.setSnapshotVitaminE(log.getSnapshotVitaminE());
        dto.setSnapshotVitaminB12(log.getSnapshotVitaminB12());
        dto.setMealType(log.getMealType());
        dto.setLogDate(log.getLogDate());
        return dto;
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private void ensureFoodAvailable(FoodItemEntity foodItem, UserEntity user) {
        if (Boolean.TRUE.equals(foodItem.getIsCustom())
                && (foodItem.getCreatedByUser() == null || !user.getId().equals(foodItem.getCreatedByUser().getId()))) {
            throw new ProductNotFoundException("Custom food item is not available to this user");
        }
        if (foodItem.getVerificationStatus() == VerificationStatus.REJECTED) {
            throw new ProductNotFoundException("Food item is not available");
        }
    }

    private void markFoodItemUsed(FoodItemEntity foodItem) {
        FoodProductQualityRules.markUsed(foodItem);
        foodItemRepository.save(foodItem);
    }

    private String normalizeMealType(String mealType) {
        return mealType == null ? null : mealType.trim().toUpperCase();
    }

    private void applyNutritionSnapshot(FoodLogsEntity entity, FoodItemEntity foodItem) {
        Double grams = entity.getNormalizedPortionGrams() != null
                ? entity.getNormalizedPortionGrams()
                : entity.getPortionSize();
        entity.setSnapshotCalories(calculateNutritionValue(foodItem.getCalories(), grams));
        entity.setSnapshotProtein(calculateNutritionValue(foodItem.getProtein(), grams));
        entity.setSnapshotCarbs(calculateNutritionValue(foodItem.getCarbs(), grams));
        entity.setSnapshotFat(calculateNutritionValue(foodItem.getFat(), grams));
        entity.setSnapshotFiber(calculateNullableNutritionValue(foodItem.getFiber(), grams));
        entity.setSnapshotSugar(calculateNullableNutritionValue(foodItem.getSugar(), grams));
        entity.setSnapshotSaturatedFat(calculateNullableNutritionValue(foodItem.getSaturatedFat(), grams));
        entity.setSnapshotSodium(calculateNullableNutritionValue(foodItem.getSodium(), grams));
        entity.setSnapshotPotassium(calculateNullableNutritionValue(foodItem.getPotassium(), grams));
        entity.setSnapshotCholesterol(calculateNullableNutritionValue(foodItem.getCholesterol(), grams));
        entity.setSnapshotCalcium(calculateNullableNutritionValue(foodItem.getCalcium(), grams));
        entity.setSnapshotIron(calculateNullableNutritionValue(foodItem.getIron(), grams));
        entity.setSnapshotMagnesium(calculateNullableNutritionValue(foodItem.getMagnesium(), grams));
        entity.setSnapshotZinc(calculateNullableNutritionValue(foodItem.getZinc(), grams));
        entity.setSnapshotVitaminA(calculateNullableNutritionValue(foodItem.getVitaminA(), grams));
        entity.setSnapshotVitaminC(calculateNullableNutritionValue(foodItem.getVitaminC(), grams));
        entity.setSnapshotVitaminD(calculateNullableNutritionValue(foodItem.getVitaminD(), grams));
        entity.setSnapshotVitaminE(calculateNullableNutritionValue(foodItem.getVitaminE(), grams));
        entity.setSnapshotVitaminB12(calculateNullableNutritionValue(foodItem.getVitaminB12(), grams));
    }

    private Double calculateNutritionValue(Double perHundredGrams, Double grams) {
        return round((perHundredGrams == null ? 0.0 : perHundredGrams)
                * (grams == null ? 0.0 : grams)
                / 100.0);
    }

    private Double calculateNullableNutritionValue(Double perHundredGrams, Double grams) {
        if (perHundredGrams == null || grams == null) {
            return null;
        }
        return round(perHundredGrams * grams / 100.0);
    }

    private Double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private Double round(Double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private int safePage(int page) {
        return Math.max(page, 0);
    }

    private int safePageSize(int size) {
        if (size < 1) {
            return DEFAULT_TEMPLATE_PAGE_SIZE;
        }
        return Math.min(size, MAX_TEMPLATE_PAGE_SIZE);
    }

}
