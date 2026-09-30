package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.RecipeCookingStepEntity;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.RecipeIngredientEntity;
import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeReviewAnalysisEngineTest {
    private final RecipeReviewAnalysisEngine engine = new RecipeReviewAnalysisEngine(new ObjectMapper());

    @Test
    void recalculatesNutritionIndependentlyAndFlagsStoredMismatch() {
        RecipeEntity recipe = recipeWithIngredient(200.0);
        recipe.setSnapshotCalories(50.0);
        recipe.setSnapshotProtein(1.0);
        recipe.setSnapshotCarbs(1.0);
        recipe.setSnapshotFat(1.0);

        RecipeReviewAnalysisEngine.AnalysisInput result = engine.analyze(recipe);

        assertThat(result.deterministicScore()).isLessThan(100);
        assertThat(result.request().getRecalculatedNutrition().getCalories()).isEqualTo(400.0);
        assertThat(result.request().getDeterministicIssues())
                .anyMatch(message -> message.contains("Stored calories differs materially"));
    }

    @Test
    void usesMillilitersForPerHundredMilliliterIngredients() {
        RecipeEntity recipe = recipeWithIngredient(0.0);
        FoodItemEntity food = recipe.getIngredients().get(0).getFoodItem();
        food.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100ML);
        recipe.getIngredients().get(0).setNormalizedPortionGrams(null);
        recipe.getIngredients().get(0).setNormalizedPortionMilliliters(250.0);
        recipe.setSnapshotCalories(500.0);
        recipe.setSnapshotProtein(25.0);
        recipe.setSnapshotCarbs(50.0);
        recipe.setSnapshotFat(15.0);

        RecipeReviewAnalysisEngine.AnalysisInput result = engine.analyze(recipe);

        assertThat(result.request().getRecalculatedNutrition().getCalories()).isEqualTo(500.0);
        assertThat(result.criticalIssue()).isFalse();
    }

    @Test
    void blocksIngredientWithoutRequiredNormalizedAmount() {
        RecipeEntity recipe = recipeWithIngredient(0.0);
        recipe.getIngredients().get(0).setNormalizedPortionGrams(null);

        RecipeReviewAnalysisEngine.AnalysisInput result = engine.analyze(recipe);

        assertThat(result.criticalIssue()).isTrue();
        assertThat(result.request().getDeterministicIssues())
                .contains("Ingredient is missing the normalized amount required by its nutrition reference unit.");
    }

    private RecipeEntity recipeWithIngredient(double storedCalories) {
        FoodItemEntity food = new FoodItemEntity();
        food.setId(10L);
        food.setName("Test food");
        food.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100G);
        food.setCalories(200.0);
        food.setProtein(10.0);
        food.setCarbs(20.0);
        food.setFat(6.0);
        food.setFiber(2.0);
        food.setSugar(3.0);

        RecipeIngredientEntity ingredient = new RecipeIngredientEntity();
        ingredient.setFoodItem(food);
        ingredient.setPortionSize(200.0);
        ingredient.setNormalizedPortionGrams(200.0);

        RecipeCookingStepEntity step = new RecipeCookingStepEntity();
        step.setInstruction("Cook until ready.");

        RecipeEntity recipe = new RecipeEntity();
        recipe.setId(1L);
        recipe.setName("Test recipe");
        recipe.setTotalYieldGrams(200.0);
        recipe.setDefaultServingGrams(100.0);
        recipe.setServingCount(2);
        recipe.setSnapshotCalories(storedCalories);
        recipe.setSnapshotProtein(20.0);
        recipe.setSnapshotCarbs(40.0);
        recipe.setSnapshotFat(12.0);
        recipe.setSnapshotFiber(4.0);
        recipe.setSnapshotSugar(6.0);
        recipe.setIngredients(List.of(ingredient));
        recipe.setCookingSteps(List.of(step));
        return recipe;
    }
}
