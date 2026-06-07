package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.RecipeLogDto;
import com.grun.calorietracker.dto.RecipeLogRequestDto;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.RecipeLogEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.repository.RecipeLogRepository;
import com.grun.calorietracker.repository.RecipeRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.RecipeLogServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeLogServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private RecipeLogRepository recipeLogRepository;
    @InjectMocks
    private RecipeLogServiceImpl service;

    @Test
    void logRecipe_scalesRecipeNutritionSnapshotByServingGrams() {
        UserEntity user = user();
        RecipeEntity recipe = recipe();
        RecipeLogRequestDto request = logRequest(200.0, null, "dinner", LocalDateTime.of(2026, 6, 7, 19, 30));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(recipeRepository.findAccessibleRecipe(5L, user)).thenReturn(Optional.of(recipe));
        when(recipeLogRepository.save(any(RecipeLogEntity.class))).thenAnswer(invocation -> {
            RecipeLogEntity log = invocation.getArgument(0);
            log.setId(8L);
            return log;
        });

        RecipeLogDto result = service.logRecipe("user@test.com", 5L, request);

        assertEquals(8L, result.getId());
        assertEquals("DINNER", result.getMealType());
        assertEquals(200.0, result.getServingGrams());
        assertEquals(250.0, result.getSnapshotCalories());
        assertEquals(20.0, result.getSnapshotProtein());
    }

    @Test
    void updateRecipeLog_recalculatesSnapshotForNewServingCount() {
        UserEntity user = user();
        RecipeEntity recipe = recipe();
        RecipeLogEntity existing = new RecipeLogEntity();
        existing.setId(9L);
        existing.setUser(user);
        existing.setRecipe(recipe);
        RecipeLogRequestDto request = logRequest(null, 2.0, "lunch", LocalDateTime.of(2026, 6, 8, 12, 15));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(recipeLogRepository.findByIdAndUser(9L, user)).thenReturn(Optional.of(existing));
        when(recipeLogRepository.save(existing)).thenReturn(existing);

        RecipeLogDto result = service.updateRecipeLog("user@test.com", 9L, request);

        assertEquals("LUNCH", result.getMealType());
        assertEquals(LocalDateTime.of(2026, 6, 8, 12, 15), result.getLogDate());
        assertEquals(400.0, result.getServingGrams());
        assertEquals(500.0, result.getSnapshotCalories());
        assertEquals(40.0, result.getSnapshotProtein());
    }

    @Test
    void logRecipe_whenServingGramsAndCountAreBothProvided_rejectsRequest() {
        RecipeLogRequestDto request = logRequest(200.0, 1.0, "breakfast", LocalDateTime.of(2026, 6, 7, 8, 0));

        assertThrows(IllegalArgumentException.class, () -> service.logRecipe("user@test.com", 5L, request));
    }

    private RecipeLogRequestDto logRequest(Double servingGrams,
                                           Double servingCount,
                                           String mealType,
                                           LocalDateTime logDate) {
        RecipeLogRequestDto request = new RecipeLogRequestDto();
        request.setServingGrams(servingGrams);
        request.setServingCount(servingCount);
        request.setMealType(mealType);
        request.setLogDate(logDate);
        return request;
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@test.com");
        return user;
    }

    private RecipeEntity recipe() {
        RecipeEntity recipe = new RecipeEntity();
        recipe.setId(5L);
        recipe.setName("Pasta");
        recipe.setVisibility(RecipeVisibility.PRIVATE);
        recipe.setTotalYieldGrams(400.0);
        recipe.setDefaultServingGrams(200.0);
        recipe.setSnapshotCalories(500.0);
        recipe.setSnapshotProtein(40.0);
        recipe.setSnapshotCarbs(80.0);
        recipe.setSnapshotFat(10.0);
        recipe.setSnapshotFiber(6.0);
        recipe.setSnapshotSugar(8.0);
        recipe.setSnapshotSodium(500.0);
        return recipe;
    }
}
