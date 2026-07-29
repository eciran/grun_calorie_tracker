package com.grun.calorietracker.service.support;

public final class GroceryListLimits {

    public static final int MAX_ACTIVE_LISTS_PER_USER = 20;
    public static final int MAX_ITEMS_PER_LIST = 300;
    public static final int MAX_ITEM_NAME_LENGTH = 160;
    public static final double MAX_DISPLAY_QUANTITY = 100_000.0;
    public static final double MAX_NORMALIZED_GRAMS = 1_000_000.0;

    private GroceryListLimits() {
    }
}
