package com.grun.calorietracker.service.support;

public final class FoodSearchLikePatterns {
    public static final char ESCAPE = '!';

    private FoodSearchLikePatterns() { }

    public static String literal(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    public static String contains(String value) {
        return "%" + literal(value) + "%";
    }
}
