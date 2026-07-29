package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.GroceryListManualItemRequestDto;
import com.grun.calorietracker.dto.GroceryListPurchaseRequestDto;
import com.grun.calorietracker.dto.GroceryListQuantityRequestDto;
import com.grun.calorietracker.dto.PersistedGroceryListDto;

public interface GroceryListService {
    PersistedGroceryListDto createFromMealPlan(String email, Long mealPlanId);
    PersistedGroceryListDto get(String email, Long listId);
    PersistedGroceryListDto addManualItem(String email, Long listId, GroceryListManualItemRequestDto request);
    PersistedGroceryListDto setPurchased(String email, Long listId, Long itemId, GroceryListPurchaseRequestDto request);
    PersistedGroceryListDto updateQuantity(String email, Long listId, Long itemId, GroceryListQuantityRequestDto request);
    PersistedGroceryListDto removeItem(String email, Long listId, Long itemId, Long expectedVersion);
    PersistedGroceryListDto complete(String email, Long listId);
    void archive(String email, Long listId);
}
