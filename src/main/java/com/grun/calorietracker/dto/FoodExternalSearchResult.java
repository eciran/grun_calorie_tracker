package com.grun.calorietracker.dto;

import java.util.List;
import java.util.Objects;

public record FoodExternalSearchResult(List<FoodProductDto> products, boolean available) {
    public FoodExternalSearchResult {
        products = List.copyOf(Objects.requireNonNull(products));
        if (!available && !products.isEmpty()) throw new IllegalArgumentException("Unavailable search cannot contain products");
    }
    public static FoodExternalSearchResult success(List<FoodProductDto> products) {
        return new FoodExternalSearchResult(products, true);
    }
    public static FoodExternalSearchResult unavailable() {
        return new FoodExternalSearchResult(List.of(), false);
    }
}
