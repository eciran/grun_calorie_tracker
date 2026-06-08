package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.RecipeLogDto;
import com.grun.calorietracker.dto.RecipeLogRequestDto;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.RecipeLogEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.RecipeLogRepository;
import com.grun.calorietracker.repository.RecipeRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.RecipeLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecipeLogServiceImpl implements RecipeLogService {

    private static final List<String> ALLOWED_MEAL_TYPES = List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK");

    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeLogRepository recipeLogRepository;

    @Override
    @Transactional
    public RecipeLogDto logRecipe(String email, Long recipeId, RecipeLogRequestDto request) {
        validateLogRequest(request);
        UserEntity user = getUser(email);
        RecipeEntity recipe = recipeRepository.findAccessibleRecipe(recipeId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

        RecipeLogEntity log = new RecipeLogEntity();
        log.setUser(user);
        log.setRecipe(recipe);
        applyRequest(log, recipe, request);
        return toDto(recipeLogRepository.save(log));
    }

    @Override
    public List<RecipeLogDto> getRecipeLogs(String email, LocalDateTime start, LocalDateTime end) {
        return recipeLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                        getUser(email),
                        start,
                        end
                ).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public RecipeLogDto updateRecipeLog(String email, Long logId, RecipeLogRequestDto request) {
        validateLogRequest(request);
        RecipeLogEntity log = recipeLogRepository.findByIdAndUser(logId, getUser(email))
                .orElseThrow(() -> new ResourceNotFoundException("Recipe log not found"));
        applyRequest(log, log.getRecipe(), request);
        return toDto(recipeLogRepository.save(log));
    }

    @Override
    @Transactional
    public void deleteRecipeLog(String email, Long logId) {
        RecipeLogEntity log = recipeLogRepository.findByIdAndUser(logId, getUser(email))
                .orElseThrow(() -> new ResourceNotFoundException("Recipe log not found"));
        recipeLogRepository.delete(log);
    }

    private void applyRequest(RecipeLogEntity log, RecipeEntity recipe, RecipeLogRequestDto request) {
        double servingGrams = resolveServingGrams(recipe, request);
        double factor = recipe.getTotalYieldGrams() == null || recipe.getTotalYieldGrams() <= 0
                ? 0
                : servingGrams / recipe.getTotalYieldGrams();

        log.setServingGrams(servingGrams);
        log.setServingCount(request.getServingCount());
        log.setMealType(normalizeMealType(request.getMealType()));
        log.setLogDate(request.getLogDate());
        log.setSnapshotCalories(scale(recipe.getSnapshotCalories(), factor));
        log.setSnapshotProtein(scale(recipe.getSnapshotProtein(), factor));
        log.setSnapshotCarbs(scale(recipe.getSnapshotCarbs(), factor));
        log.setSnapshotFat(scale(recipe.getSnapshotFat(), factor));
        log.setSnapshotFiber(scale(recipe.getSnapshotFiber(), factor));
        log.setSnapshotSugar(scale(recipe.getSnapshotSugar(), factor));
        log.setSnapshotSodium(scale(recipe.getSnapshotSodium(), factor));
    }

    private void validateLogRequest(RecipeLogRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Recipe log request is required.");
        }
        if (request.getLogDate() == null) {
            throw new IllegalArgumentException("Recipe log date is required.");
        }
        String mealType = normalizeMealType(request.getMealType());
        if (mealType == null || !ALLOWED_MEAL_TYPES.contains(mealType)) {
            throw new IllegalArgumentException("Meal type must be one of BREAKFAST, LUNCH, DINNER, or SNACK.");
        }
        if (request.getServingGrams() != null && request.getServingCount() != null) {
            throw new IllegalArgumentException("Use either servingGrams or servingCount, not both.");
        }
        if (request.getServingGrams() != null && request.getServingGrams() <= 0) {
            throw new IllegalArgumentException("Serving grams must be greater than zero.");
        }
        if (request.getServingCount() != null && request.getServingCount() <= 0) {
            throw new IllegalArgumentException("Serving count must be greater than zero.");
        }
    }

    private double resolveServingGrams(RecipeEntity recipe, RecipeLogRequestDto request) {
        if (request.getServingGrams() != null && request.getServingGrams() > 0) {
            return request.getServingGrams();
        }
        double servingCount = request.getServingCount() == null ? 1.0 : request.getServingCount();
        if (recipe.getDefaultServingGrams() != null && recipe.getDefaultServingGrams() > 0) {
            return servingCount * recipe.getDefaultServingGrams();
        }
        if (recipe.getTotalYieldGrams() != null && recipe.getTotalYieldGrams() > 0) {
            return servingCount * recipe.getTotalYieldGrams();
        }
        throw new IllegalArgumentException("Recipe serving size could not be resolved.");
    }

    private Double scale(Double value, double factor) {
        return value == null ? 0.0 : Math.round(value * factor * 100.0) / 100.0;
    }

    private RecipeLogDto toDto(RecipeLogEntity log) {
        RecipeLogDto dto = new RecipeLogDto();
        dto.setId(log.getId());
        dto.setRecipeId(log.getRecipe().getId());
        dto.setRecipeName(log.getRecipe().getName());
        dto.setServingGrams(log.getServingGrams());
        dto.setServingCount(log.getServingCount());
        dto.setMealType(log.getMealType());
        dto.setLogDate(log.getLogDate());
        dto.setSnapshotCalories(log.getSnapshotCalories());
        dto.setSnapshotProtein(log.getSnapshotProtein());
        dto.setSnapshotCarbs(log.getSnapshotCarbs());
        dto.setSnapshotFat(log.getSnapshotFat());
        dto.setSnapshotFiber(log.getSnapshotFiber());
        dto.setSnapshotSugar(log.getSnapshotSugar());
        dto.setSnapshotSodium(log.getSnapshotSodium());
        return dto;
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private String normalizeMealType(String mealType) {
        return mealType == null || mealType.isBlank() ? null : mealType.trim().toUpperCase();
    }
}
