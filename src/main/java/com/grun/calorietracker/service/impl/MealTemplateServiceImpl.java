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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MealTemplateServiceImpl implements MealTemplateService {

    private final UserRepository userRepository;
    private final FoodLogsRepository foodLogsRepository;
    private final FoodItemRepository foodItemRepository;
    private final MealTemplateRepository mealTemplateRepository;

    @Override
    @Transactional
    public MealTemplateDto createFromLoggedMeal(String email, MealTemplateCreateRequestDto request) {
        UserEntity user = getUser(email);
        String mealType = normalizeMealType(request.getMealType());
        List<FoodLogsEntity> sourceLogs = foodLogsRepository.findByUserAndMealTypeAndLogDateBetween(
                user,
                mealType,
                request.getSourceDate().atStartOfDay(),
                request.getSourceDate().plusDays(1).atStartOfDay()
        );
        if (sourceLogs.isEmpty()) {
            throw new IllegalArgumentException("Source meal has no diary entries");
        }

        MealTemplateEntity template = new MealTemplateEntity();
        template.setUser(user);
        template.setName(request.getName().trim());
        template.setMealType(mealType);
        List<MealTemplateItemEntity> items = new ArrayList<>();
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
        template.setItems(items);
        return toDto(mealTemplateRepository.save(template));
    }

    @Override
    public List<MealTemplateDto> getTemplates(String email) {
        return mealTemplateRepository.findByUserOrderByCreatedAtDesc(getUser(email)).stream()
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
        dto.setItems(template.getItems().stream().map(this::toItemDto).toList());
        return dto;
    }

    private MealTemplateItemDto toItemDto(MealTemplateItemEntity item) {
        MealTemplateItemDto dto = new MealTemplateItemDto();
        dto.setFoodItemId(item.getFoodItem().getId());
        dto.setFoodName(item.getFoodItem().getName());
        dto.setPortionSize(item.getPortionSize());
        dto.setPortionUnit(FoodPortionCalculator.resolveUnit(item.getPortionUnit()));
        dto.setNormalizedPortionGrams(item.getNormalizedPortionGrams());
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

}
