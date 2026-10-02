package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.AiRecipeQualityReviewRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.RecipeIngredientEntity;
import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;
import com.grun.calorietracker.enums.PreferredLanguage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class RecipeReviewAnalysisEngine {
    private final ObjectMapper objectMapper;

    RecipeReviewAnalysisEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    AnalysisInput analyze(RecipeEntity recipe) {
        return analyze(recipe, PreferredLanguage.EN);
    }

    AnalysisInput analyze(RecipeEntity recipe, PreferredLanguage responseLanguage) {
        List<Issue> issues = new ArrayList<>();
        Nutrition recalculated = new Nutrition();
        if (recipe.getIngredients() == null || recipe.getIngredients().isEmpty()) {
            issues.add(new Issue("MISSING_INGREDIENTS", "CRITICAL", null, "Recipe has no ingredients."));
        } else {
            for (RecipeIngredientEntity ingredient : recipe.getIngredients()) {
                accumulate(recipe, ingredient, recalculated, issues);
            }
        }
        if (recipe.getCookingSteps() == null || recipe.getCookingSteps().isEmpty()) {
            issues.add(new Issue("MISSING_STEPS", "HIGH", null, "Recipe has no cooking steps."));
        }
        validatePositive("total yield", recipe.getTotalYieldGrams(), issues);
        validatePositive("default serving", recipe.getDefaultServingGrams(), issues);
        if (recipe.getTotalYieldGrams() != null && recipe.getDefaultServingGrams() != null
                && recipe.getDefaultServingGrams() > recipe.getTotalYieldGrams()) {
            issues.add(new Issue("SERVING_EXCEEDS_YIELD", "HIGH", null,
                    "Default serving is larger than total recipe yield."));
        }
        compare("calories", recipe.getSnapshotCalories(), recalculated.calories, 10.0, 0.08, issues);
        compare("protein", recipe.getSnapshotProtein(), recalculated.protein, 3.0, 0.10, issues);
        compare("carbs", recipe.getSnapshotCarbs(), recalculated.carbs, 3.0, 0.10, issues);
        compare("fat", recipe.getSnapshotFat(), recalculated.fat, 3.0, 0.10, issues);

        int score = 100;
        for (Issue issue : issues) {
            score -= switch (issue.severity()) {
                case "CRITICAL" -> 40;
                case "HIGH" -> 20;
                case "MEDIUM" -> 10;
                default -> 4;
            };
        }
        score = Math.max(0, score);
        boolean critical = issues.stream().anyMatch(issue -> "CRITICAL".equals(issue.severity()));
        PreferredLanguage resolvedLanguage = responseLanguage == null ? PreferredLanguage.EN : responseLanguage;
        AiRecipeQualityReviewRequestDto request = request(recipe, recalculated, issues, resolvedLanguage);
        Map<String, Object> deterministic = new LinkedHashMap<>();
        deterministic.put("storedNutrition", nutritionMap(recipe));
        deterministic.put("recalculatedNutrition", recalculated);
        deterministic.put("issues", issues);
        deterministic.put("score", score);
        return new AnalysisInput(contentHash(recipe, resolvedLanguage), request, deterministic, score, critical);
    }

    private void accumulate(RecipeEntity recipe, RecipeIngredientEntity ingredient, Nutrition total, List<Issue> issues) {
        Double factor = factor(ingredient);
        if (factor == null) {
            issues.add(new Issue("MISSING_NORMALIZED_AMOUNT", "CRITICAL", ingredientFoodId(ingredient),
                    "Ingredient is missing the normalized amount required by its nutrition reference unit."));
            return;
        }
        FoodItemEntity food = ingredient.getFoodItem();
        Double calories = food == null ? ingredient.getSnapshotCalories() : food.getCalories();
        Double protein = food == null ? ingredient.getSnapshotProtein() : food.getProtein();
        Double carbs = food == null ? ingredient.getSnapshotCarbs() : food.getCarbs();
        Double fat = food == null ? ingredient.getSnapshotFat() : food.getFat();
        if (calories == null || protein == null || carbs == null || fat == null) {
            issues.add(new Issue("INCOMPLETE_MACROS", "HIGH", ingredientFoodId(ingredient),
                    "Ingredient is missing calories or a required macro value."));
        }
        total.calories += value(calories) * factor;
        total.protein += value(protein) * factor;
        total.carbs += value(carbs) * factor;
        total.fat += value(fat) * factor;
        total.fiber += value(food == null ? ingredient.getSnapshotFiber() : food.getFiber()) * factor;
        total.sugar += value(food == null ? ingredient.getSnapshotSugar() : food.getSugar()) * factor;
    }

    private AiRecipeQualityReviewRequestDto request(RecipeEntity recipe, Nutrition recalculated, List<Issue> issues,
                                                    PreferredLanguage responseLanguage) {
        AiRecipeQualityReviewRequestDto dto = new AiRecipeQualityReviewRequestDto();
        dto.setRecipeId(recipe.getId());
        dto.setName(recipe.getName());
        dto.setDescription(recipe.getDescription());
        dto.setLanguage(responseLanguage.name().toLowerCase(java.util.Locale.ROOT));
        dto.setMarketRegion(recipe.getMarketRegion() == null ? null : recipe.getMarketRegion().name());
        dto.setTotalYieldGrams(recipe.getTotalYieldGrams());
        dto.setDefaultServingGrams(recipe.getDefaultServingGrams());
        dto.setServingCount(recipe.getServingCount());
        dto.setStoredNutrition(toNutrition(recipe));
        dto.setRecalculatedNutrition(toNutrition(recalculated));
        dto.setDeterministicIssues(issues.stream().map(Issue::message).toList());
        dto.setAllergens(recipe.getAllergens() == null ? List.of() : recipe.getAllergens().stream().map(Enum::name).toList());
        dto.setCookingSteps(recipe.getCookingSteps() == null ? List.of() : recipe.getCookingSteps().stream().map(step -> step.getInstruction()).toList());
        dto.setIngredients(recipe.getIngredients() == null ? List.of() : recipe.getIngredients().stream().map(this::ingredient).toList());
        return dto;
    }

    private AiRecipeQualityReviewRequestDto.Ingredient ingredient(RecipeIngredientEntity entity) {
        AiRecipeQualityReviewRequestDto.Ingredient dto = new AiRecipeQualityReviewRequestDto.Ingredient();
        FoodItemEntity food = entity.getFoodItem();
        dto.setFoodItemId(food == null ? null : food.getId());
        dto.setName(food == null ? entity.getSnapshotFoodName() : food.getName());
        dto.setPortionSize(entity.getPortionSize());
        dto.setPortionUnit(entity.getPortionUnit() == null ? null : entity.getPortionUnit().name());
        dto.setNormalizedGrams(entity.getNormalizedPortionGrams());
        dto.setNormalizedMilliliters(entity.getNormalizedPortionMilliliters());
        dto.setVerificationStatus(food == null || food.getVerificationStatus() == null ? null : food.getVerificationStatus().name());
        dto.setQualityScore(food == null ? null : food.getQualityScore());
        return dto;
    }

    private String contentHash(RecipeEntity recipe, PreferredLanguage responseLanguage) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", recipe.getName());
        source.put("description", recipe.getDescription());
        source.put("analysisLanguage", responseLanguage == null ? PreferredLanguage.EN.name() : responseLanguage.name());
        source.put("yield", recipe.getTotalYieldGrams());
        source.put("serving", recipe.getDefaultServingGrams());
        source.put("count", recipe.getServingCount());
        source.put("nutrition", nutritionMap(recipe));
        source.put("ingredients", recipe.getIngredients() == null ? List.of() : recipe.getIngredients().stream().map(item -> List.of(
                item.getFoodItem() == null ? 0L : item.getFoodItem().getId(),
                item.getSnapshotFoodName() == null ? "" : item.getSnapshotFoodName(),
                item.getPortionSize() == null ? 0.0 : item.getPortionSize(),
                item.getPortionUnit() == null ? "" : item.getPortionUnit().name(),
                item.getNormalizedPortionGrams() == null ? 0.0 : item.getNormalizedPortionGrams(),
                item.getNormalizedPortionMilliliters() == null ? 0.0 : item.getNormalizedPortionMilliliters())).toList());
        source.put("steps", recipe.getCookingSteps() == null ? List.of() : recipe.getCookingSteps().stream().map(step -> step.getInstruction()).toList());
        try {
            byte[] payload = objectMapper.writeValueAsBytes(source);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not hash recipe review content.", exception);
        }
    }

    private Double factor(RecipeIngredientEntity ingredient) {
        FoodItemEntity food = ingredient.getFoodItem();
        boolean liquid = food != null && food.getNutritionReferenceUnit() == FoodNutritionReferenceUnit.PER_100ML;
        Double amount = liquid ? ingredient.getNormalizedPortionMilliliters() : ingredient.getNormalizedPortionGrams();
        return amount == null || amount <= 0 ? null : amount / 100.0;
    }

    private void compare(String field, Double stored, double recalculated, double absolute, double ratio, List<Issue> issues) {
        if (stored == null) {
            issues.add(new Issue("MISSING_STORED_NUTRITION", "HIGH", null, "Stored " + field + " is missing."));
            return;
        }
        double tolerance = Math.max(absolute, Math.abs(recalculated) * ratio);
        if (Math.abs(stored - recalculated) > tolerance) {
            issues.add(new Issue("NUTRITION_MISMATCH", "HIGH", null,
                    "Stored " + field + " differs materially from the ingredient-based total."));
        }
    }

    private void validatePositive(String field, Double value, List<Issue> issues) {
        if (value == null || value <= 0) {
            issues.add(new Issue("INVALID_PORTION", "HIGH", null, "Recipe " + field + " must be greater than zero."));
        }
    }

    private Map<String, Double> nutritionMap(RecipeEntity recipe) {
        return Map.of("calories", value(recipe.getSnapshotCalories()), "protein", value(recipe.getSnapshotProtein()),
                "carbs", value(recipe.getSnapshotCarbs()), "fat", value(recipe.getSnapshotFat()),
                "fiber", value(recipe.getSnapshotFiber()), "sugar", value(recipe.getSnapshotSugar()));
    }

    private AiRecipeQualityReviewRequestDto.Nutrition toNutrition(RecipeEntity recipe) {
        AiRecipeQualityReviewRequestDto.Nutrition dto = new AiRecipeQualityReviewRequestDto.Nutrition();
        dto.setCalories(recipe.getSnapshotCalories()); dto.setProtein(recipe.getSnapshotProtein());
        dto.setCarbs(recipe.getSnapshotCarbs()); dto.setFat(recipe.getSnapshotFat());
        dto.setFiber(recipe.getSnapshotFiber()); dto.setSugar(recipe.getSnapshotSugar());
        return dto;
    }

    private AiRecipeQualityReviewRequestDto.Nutrition toNutrition(Nutrition source) {
        AiRecipeQualityReviewRequestDto.Nutrition dto = new AiRecipeQualityReviewRequestDto.Nutrition();
        dto.setCalories(round(source.calories)); dto.setProtein(round(source.protein));
        dto.setCarbs(round(source.carbs)); dto.setFat(round(source.fat));
        dto.setFiber(round(source.fiber)); dto.setSugar(round(source.sugar));
        return dto;
    }

    private Long ingredientFoodId(RecipeIngredientEntity ingredient) { return ingredient.getFoodItem() == null ? null : ingredient.getFoodItem().getId(); }
    private double value(Double value) { return value == null ? 0.0 : value; }
    private double round(double value) { return Math.round(value * 100.0) / 100.0; }

    record Issue(String type, String severity, Long foodItemId, String message) {}
    record AnalysisInput(String contentHash, AiRecipeQualityReviewRequestDto request, Map<String, Object> deterministicResult,
                         int deterministicScore, boolean criticalIssue) {}
    static final class Nutrition {
        public double calories;
        public double protein;
        public double carbs;
        public double fat;
        public double fiber;
        public double sugar;
    }
}
