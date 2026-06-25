package com.grun.calorietracker.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Standard recipe discovery categories used by public recipe filters.")
public enum RecipeCategory {
    VEGAN,
    VEGETARIAN,
    HIGH_PROTEIN,
    LOW_CARB,
    LOW_FAT,
    LOW_CALORIE,
    HIGH_FIBER,
    GLUTEN_FREE,
    DAIRY_FREE,
    BREAKFAST,
    LUNCH,
    DINNER,
    SNACK,
    VEGETABLES,
    MEAT,
    CHICKEN,
    FISH,
    SOUP,
    SALAD,
    DESSERT,
    QUICK_MEAL,
    MEAL_PREP,
    TURKISH,
    MEDITERRANEAN,
    UK_IE
}
