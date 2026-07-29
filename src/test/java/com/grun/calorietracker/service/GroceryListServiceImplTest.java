package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.GroceryListDto;
import com.grun.calorietracker.dto.GroceryListItemDto;
import com.grun.calorietracker.dto.GroceryListManualItemRequestDto;
import com.grun.calorietracker.dto.GroceryListPurchaseRequestDto;
import com.grun.calorietracker.dto.GroceryListRefreshRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.GroceryListEntity;
import com.grun.calorietracker.entity.GroceryListItemEntity;
import com.grun.calorietracker.entity.MealPlanEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.GroceryCategory;
import com.grun.calorietracker.enums.GroceryListItemSource;
import com.grun.calorietracker.enums.GroceryListStatus;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.GroceryListItemRepository;
import com.grun.calorietracker.repository.GroceryListRepository;
import com.grun.calorietracker.repository.MealPlanRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.GroceryListServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroceryListServiceImplTest {

    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MealPlanRepository mealPlanRepository = mock(MealPlanRepository.class);
    private final FoodItemRepository foodItemRepository = mock(FoodItemRepository.class);
    private final GroceryListRepository groceryListRepository = mock(GroceryListRepository.class);
    private final GroceryListItemRepository groceryListItemRepository = mock(GroceryListItemRepository.class);
    private final MealPlanService mealPlanService = mock(MealPlanService.class);
    private final GroceryListServiceImpl service = new GroceryListServiceImpl(
            subscriptionService, userRepository, mealPlanRepository, foodItemRepository,
            groceryListRepository, groceryListItemRepository, mealPlanService);

    private UserEntity user;
    private MealPlanEntity mealPlan;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(7L);
        user.setEmail("user@grun.app");
        mealPlan = new MealPlanEntity();
        mealPlan.setId(12L);
        mealPlan.setUser(user);
        mealPlan.setName("Week plan");
        mealPlan.setUpdatedAt(LocalDateTime.of(2026, 7, 29, 12, 0));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(groceryListRepository.save(any())).thenAnswer(invocation -> prepareSaved(invocation.getArgument(0)));
    }

    @Test
    void createFromMealPlan_persistsGeneratedItemsAndEnforcesEntitlement() {
        FoodItemEntity food = new FoodItemEntity();
        food.setId(5L);
        food.setName("Greek yogurt");
        GroceryListDto generated = new GroceryListDto();
        generated.setMealPlanId(12L);
        generated.setMealPlanName("Week plan");
        generated.setItems(List.of(new GroceryListItemDto(
                5L, "Greek yogurt", 340.0, 2.0, FoodPortionUnit.SERVING, 2)));
        when(mealPlanRepository.findByIdAndUser(12L, user)).thenReturn(Optional.of(mealPlan));
        when(groceryListRepository.findByUserAndSourceMealPlanAndStatus(user, mealPlan, GroceryListStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(groceryListRepository.countByUserAndStatus(user, GroceryListStatus.ACTIVE)).thenReturn(0L);
        when(mealPlanService.getGroceryList(user.getEmail(), 12L)).thenReturn(generated);
        when(foodItemRepository.findById(5L)).thenReturn(Optional.of(food));

        var result = service.createFromMealPlan(user.getEmail(), 12L);

        verify(subscriptionService).assertFeatureAccess(user.getEmail(), SubscriptionFeature.GROCERY_LIST);
        assertEquals(1, result.getItems().size());
        assertEquals(GroceryListItemSource.GENERATED, result.getItems().get(0).getSource());
        assertEquals(2.0, result.getItems().get(0).getDisplayQuantity());
        assertEquals(FoodPortionUnit.SERVING, result.getItems().get(0).getDisplayUnit());
    }

    @Test
    void addManualItem_addsEditableNonCatalogItem() {
        GroceryListEntity list = list();
        when(groceryListRepository.findOwnedForUpdate(20L, user)).thenReturn(Optional.of(list));
        when(groceryListItemRepository.countByGroceryList(list)).thenReturn(1L);
        GroceryListManualItemRequestDto request = new GroceryListManualItemRequestDto();
        request.setDisplayName("Coffee filters");
        request.setCategory(GroceryCategory.PANTRY);
        request.setDisplayQuantity(1.0);
        request.setDisplayUnit(FoodPortionUnit.PIECE);

        var result = service.addManualItem(user.getEmail(), 20L, request);

        assertEquals(2, result.getItems().size());
        assertEquals(GroceryListItemSource.MANUAL, result.getItems().get(1).getSource());
        assertEquals("Coffee filters", result.getItems().get(1).getDisplayName());
    }

    @Test
    void setPurchased_rejectsStaleClientVersion() {
        GroceryListEntity list = list();
        GroceryListItemEntity item = list.getItems().get(0);
        item.setVersion(3L);
        when(groceryListItemRepository.findOwnedForUpdate(30L, 20L, user)).thenReturn(Optional.of(item));
        GroceryListPurchaseRequestDto request = new GroceryListPurchaseRequestDto();
        request.setPurchased(true);
        request.setExpectedVersion(2L);

        assertThrows(RequestConflictException.class,
                () -> service.setPurchased(user.getEmail(), 20L, 30L, request));
    }

    @Test
    void removeGeneratedItem_excludesWithoutDeletingSourceHistory() {
        GroceryListEntity list = list();
        GroceryListItemEntity item = list.getItems().get(0);
        when(groceryListItemRepository.findOwnedForUpdate(30L, 20L, user)).thenReturn(Optional.of(item));

        var result = service.removeItem(user.getEmail(), 20L, 30L, 0L);

        assertEquals(true, result.getItems().get(0).getExcluded());
        verify(groceryListItemRepository).save(item);
    }

    @Test
    void get_marksListOutdatedWhenMealPlanChanged() {
        GroceryListEntity list = list();
        mealPlan.setUpdatedAt(list.getSourceUpdatedAt().plusMinutes(1));
        when(groceryListRepository.findByIdAndUser(20L, user)).thenReturn(Optional.of(list));

        var result = service.get(user.getEmail(), 20L);

        assertTrue(result.getSourceOutdated());
        assertEquals(mealPlan.getUpdatedAt(), result.getCurrentSourceUpdatedAt());
    }

    @Test
    void refresh_mergesSourceWhilePreservingUserDecisions() {
        GroceryListEntity list = list();
        GroceryListItemEntity retained = list.getItems().get(0);
        retained.setPurchased(true);
        retained.setExcluded(true);
        retained.setQuantityOverridden(true);
        retained.setDisplayQuantity(9.0);

        GroceryListItemEntity removed = generatedItem(31L, 8L, "Old ingredient");
        list.addItem(removed);
        GroceryListItemEntity manual = new GroceryListItemEntity();
        manual.setId(32L);
        manual.setSource(GroceryListItemSource.MANUAL);
        manual.setDisplayName("Reusable bag");
        manual.setCategory(GroceryCategory.OTHER);
        manual.setDisplayQuantity(1.0);
        manual.setDisplayUnit(FoodPortionUnit.PIECE);
        manual.setPurchased(true);
        manual.setVersion(0L);
        list.addItem(manual);

        GroceryListDto generated = new GroceryListDto();
        generated.setItems(List.of(
                new GroceryListItemDto(5L, "Updated yogurt", 500.0, 3.0, FoodPortionUnit.SERVING, 3),
                new GroceryListItemDto(6L, "Bananas", 240.0, 2.0, FoodPortionUnit.PIECE, 2)));
        mealPlan.setUpdatedAt(mealPlan.getUpdatedAt().plusDays(1));
        when(groceryListRepository.findOwnedForUpdate(20L, user)).thenReturn(Optional.of(list));
        when(mealPlanService.getGroceryList(user.getEmail(), 12L)).thenReturn(generated);
        when(foodItemRepository.findById(any())).thenReturn(Optional.empty());
        GroceryListRefreshRequestDto request = new GroceryListRefreshRequestDto();
        request.setExpectedVersion(0L);

        var result = service.refresh(user.getEmail(), 20L, request);

        var retainedResult = result.getItems().stream()
                .filter(item -> "Updated yogurt".equals(item.getDisplayName())).findFirst().orElseThrow();
        assertTrue(retainedResult.getPurchased());
        assertTrue(retainedResult.getExcluded());
        assertEquals(9.0, retainedResult.getDisplayQuantity());
        assertFalse(retainedResult.getSourceRemoved());
        assertTrue(result.getItems().stream().anyMatch(item -> "Bananas".equals(item.getDisplayName())));
        assertTrue(result.getItems().stream()
                .filter(item -> "Old ingredient".equals(item.getDisplayName()))
                .findFirst().orElseThrow().getSourceRemoved());
        assertTrue(result.getItems().stream().anyMatch(item -> "Reusable bag".equals(item.getDisplayName())));
        assertFalse(result.getSourceOutdated());
    }

    @Test
    void refresh_rejectsStaleListVersion() {
        GroceryListEntity list = list();
        list.setVersion(4L);
        when(groceryListRepository.findOwnedForUpdate(20L, user)).thenReturn(Optional.of(list));
        GroceryListRefreshRequestDto request = new GroceryListRefreshRequestDto();
        request.setExpectedVersion(3L);

        assertThrows(RequestConflictException.class,
                () -> service.refresh(user.getEmail(), 20L, request));
    }

    private GroceryListItemEntity generatedItem(Long id, Long foodId, String name) {
        FoodItemEntity food = new FoodItemEntity();
        food.setId(foodId);
        GroceryListItemEntity item = new GroceryListItemEntity();
        item.setId(id);
        item.setFoodItem(food);
        item.setSource(GroceryListItemSource.GENERATED);
        item.setGeneratedSourceKey("food:" + foodId);
        item.setDisplayName(name);
        item.setCategory(GroceryCategory.OTHER);
        item.setDisplayQuantity(1.0);
        item.setDisplayUnit(FoodPortionUnit.PIECE);
        item.setPurchased(false);
        item.setExcluded(false);
        item.setSourceRemoved(false);
        item.setQuantityOverridden(false);
        item.setPlannedUses(1);
        item.setVersion(0L);
        return item;
    }
    private GroceryListEntity list() {
        GroceryListEntity list = new GroceryListEntity();
        list.setId(20L);
        list.setUser(user);
        list.setSourceMealPlan(mealPlan);
        list.setSourceUpdatedAt(mealPlan.getUpdatedAt());
        list.setStatus(GroceryListStatus.ACTIVE);
        list.setVersion(0L);
        GroceryListItemEntity item = new GroceryListItemEntity();
        item.setId(30L);
        item.setSource(GroceryListItemSource.GENERATED);
        item.setGeneratedSourceKey("food:5");
        item.setDisplayName("Greek yogurt");
        item.setCategory(GroceryCategory.DAIRY_AND_EGGS);
        item.setDisplayQuantity(2.0);
        item.setDisplayUnit(FoodPortionUnit.SERVING);
        item.setNormalizedGrams(340.0);
        item.setPurchased(false);
        item.setExcluded(false);
        item.setQuantityOverridden(false);
        item.setPlannedUses(2);
        item.setVersion(0L);
        list.addItem(item);
        return list;
    }

    private GroceryListEntity prepareSaved(GroceryListEntity list) {
        if (list.getId() == null) list.setId(20L);
        int id = 30;
        for (GroceryListItemEntity item : list.getItems()) {
            if (item.getId() == null) item.setId((long) id++);
            if (item.getVersion() == null) item.setVersion(0L);
        }
        return list;
    }
}
