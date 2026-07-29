package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.GroceryListDto;
import com.grun.calorietracker.dto.GroceryListItemDto;
import com.grun.calorietracker.dto.GroceryListItemResponseDto;
import com.grun.calorietracker.dto.GroceryListManualItemRequestDto;
import com.grun.calorietracker.dto.GroceryListPurchaseRequestDto;
import com.grun.calorietracker.dto.GroceryListQuantityRequestDto;
import com.grun.calorietracker.dto.PersistedGroceryListDto;
import com.grun.calorietracker.entity.GroceryListEntity;
import com.grun.calorietracker.entity.GroceryListItemEntity;
import com.grun.calorietracker.entity.MealPlanEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.GroceryCategory;
import com.grun.calorietracker.enums.GroceryListItemSource;
import com.grun.calorietracker.enums.GroceryListSourceType;
import com.grun.calorietracker.enums.GroceryListStatus;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.GroceryListItemRepository;
import com.grun.calorietracker.repository.GroceryListRepository;
import com.grun.calorietracker.repository.MealPlanRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.GroceryListService;
import com.grun.calorietracker.service.MealPlanService;
import com.grun.calorietracker.service.SubscriptionService;
import com.grun.calorietracker.service.support.GroceryListLimits;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GroceryListServiceImpl implements GroceryListService {

    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;
    private final MealPlanRepository mealPlanRepository;
    private final FoodItemRepository foodItemRepository;
    private final GroceryListRepository groceryListRepository;
    private final GroceryListItemRepository groceryListItemRepository;
    private final MealPlanService mealPlanService;

    @Override
    @Transactional
    public PersistedGroceryListDto createFromMealPlan(String email, Long mealPlanId) {
        assertAccess(email);
        UserEntity user = getUser(email);
        MealPlanEntity mealPlan = mealPlanRepository.findByIdAndUser(mealPlanId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Meal plan not found"));
        return groceryListRepository.findByUserAndSourceMealPlanAndStatus(user, mealPlan, GroceryListStatus.ACTIVE)
                .map(this::toDto)
                .orElseGet(() -> createList(email, user, mealPlan));
    }

    @Override
    @Transactional(readOnly = true)
    public PersistedGroceryListDto get(String email, Long listId) {
        assertAccess(email);
        return toDto(getOwned(listId, getUser(email)));
    }

    @Override
    @Transactional
    public PersistedGroceryListDto addManualItem(String email, Long listId, GroceryListManualItemRequestDto request) {
        assertAccess(email);
        UserEntity user = getUser(email);
        GroceryListEntity list = getOwnedForUpdate(listId, user);
        assertEditable(list);
        assertItemCapacity(list);

        GroceryListItemEntity item = new GroceryListItemEntity();
        item.setSource(GroceryListItemSource.MANUAL);
        item.setDisplayName(request.getDisplayName().trim());
        item.setCategory(request.getCategory());
        item.setDisplayQuantity(request.getDisplayQuantity());
        item.setDisplayUnit(request.getDisplayUnit());
        item.setNormalizedGrams(request.getNormalizedGrams());
        item.setPlannedUses(0);
        list.addItem(item);
        return toDto(groceryListRepository.save(list));
    }

    @Override
    @Transactional
    public PersistedGroceryListDto setPurchased(String email, Long listId, Long itemId,
                                                 GroceryListPurchaseRequestDto request) {
        assertAccess(email);
        UserEntity user = getUser(email);
        GroceryListItemEntity item = getOwnedItemForUpdate(itemId, listId, user);
        assertEditable(item.getGroceryList());
        assertVersion(item, request.getExpectedVersion());
        item.setPurchased(request.getPurchased());
        groceryListItemRepository.save(item);
        return toDto(item.getGroceryList());
    }

    @Override
    @Transactional
    public PersistedGroceryListDto updateQuantity(String email, Long listId, Long itemId,
                                                   GroceryListQuantityRequestDto request) {
        assertAccess(email);
        UserEntity user = getUser(email);
        GroceryListItemEntity item = getOwnedItemForUpdate(itemId, listId, user);
        assertEditable(item.getGroceryList());
        assertVersion(item, request.getExpectedVersion());
        item.setDisplayQuantity(request.getDisplayQuantity());
        item.setDisplayUnit(request.getDisplayUnit());
        item.setNormalizedGrams(request.getNormalizedGrams());
        if (request.getCategory() != null) item.setCategory(request.getCategory());
        item.setQuantityOverridden(true);
        groceryListItemRepository.save(item);
        return toDto(item.getGroceryList());
    }

    @Override
    @Transactional
    public PersistedGroceryListDto removeItem(String email, Long listId, Long itemId, Long expectedVersion) {
        assertAccess(email);
        UserEntity user = getUser(email);
        GroceryListItemEntity item = getOwnedItemForUpdate(itemId, listId, user);
        GroceryListEntity list = item.getGroceryList();
        assertEditable(list);
        assertVersion(item, expectedVersion);
        if (item.getSource() == GroceryListItemSource.MANUAL) {
            list.removeItem(item);
            groceryListItemRepository.delete(item);
        } else {
            item.setExcluded(true);
            item.setPurchased(false);
            groceryListItemRepository.save(item);
        }
        return toDto(list);
    }

    @Override
    @Transactional
    public PersistedGroceryListDto complete(String email, Long listId) {
        assertAccess(email);
        GroceryListEntity list = getOwnedForUpdate(listId, getUser(email));
        assertEditable(list);
        list.setStatus(GroceryListStatus.COMPLETED);
        return toDto(groceryListRepository.save(list));
    }

    @Override
    @Transactional
    public void archive(String email, Long listId) {
        assertAccess(email);
        GroceryListEntity list = getOwnedForUpdate(listId, getUser(email));
        list.setStatus(GroceryListStatus.ARCHIVED);
        groceryListRepository.save(list);
    }

    private PersistedGroceryListDto createList(String email, UserEntity user, MealPlanEntity mealPlan) {
        if (groceryListRepository.countByUserAndStatus(user, GroceryListStatus.ACTIVE)
                >= GroceryListLimits.MAX_ACTIVE_LISTS_PER_USER) {
            throw new IllegalArgumentException("Maximum active grocery list limit reached");
        }
        GroceryListDto generated = mealPlanService.getGroceryList(email, mealPlan.getId());
        if (generated.getItems().size() > GroceryListLimits.MAX_ITEMS_PER_LIST) {
            throw new IllegalArgumentException("Grocery list item limit exceeded");
        }

        GroceryListEntity list = new GroceryListEntity();
        list.setUser(user);
        list.setSourceType(GroceryListSourceType.MEAL_PLAN);
        list.setSourceMealPlan(mealPlan);
        list.setSourceUpdatedAt(mealPlan.getUpdatedAt() == null ? LocalDateTime.now() : mealPlan.getUpdatedAt());
        list.setStatus(GroceryListStatus.ACTIVE);
        generated.getItems().forEach(item -> list.addItem(toGeneratedItem(item)));
        return toDto(groceryListRepository.save(list));
    }

    private GroceryListItemEntity toGeneratedItem(GroceryListItemDto source) {
        GroceryListItemEntity item = new GroceryListItemEntity();
        item.setSource(GroceryListItemSource.GENERATED);
        item.setGeneratedSourceKey("food:" + source.getFoodItemId());
        item.setFoodItem(source.getFoodItemId() == null ? null : foodItemRepository.findById(source.getFoodItemId()).orElse(null));
        item.setDisplayName(source.getName());
        item.setCategory(GroceryCategory.OTHER);
        item.setDisplayQuantity(positive(source.getTotalQuantity()) ? source.getTotalQuantity() : source.getTotalGrams());
        item.setDisplayUnit(positive(source.getTotalQuantity()) && source.getQuantityUnit() != null
                ? source.getQuantityUnit() : FoodPortionUnit.GRAM);
        item.setNormalizedGrams(source.getTotalGrams());
        item.setPlannedUses(source.getPlannedUses() == null ? 0 : source.getPlannedUses());
        return item;
    }

    private PersistedGroceryListDto toDto(GroceryListEntity entity) {
        List<GroceryListItemResponseDto> items = entity.getItems().stream()
                .sorted(Comparator.comparing(GroceryListItemEntity::getCategory)
                        .thenComparing(GroceryListItemEntity::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toItemDto)
                .toList();
        PersistedGroceryListDto dto = new PersistedGroceryListDto();
        dto.setId(entity.getId());
        dto.setSourceMealPlanId(entity.getSourceMealPlan().getId());
        dto.setSourceMealPlanName(entity.getSourceMealPlan().getName());
        dto.setSourceUpdatedAt(entity.getSourceUpdatedAt());
        dto.setStatus(entity.getStatus());
        dto.setTotalItems(items.size());
        dto.setVisibleItems((int) items.stream().filter(item -> !Boolean.TRUE.equals(item.getExcluded())).count());
        dto.setPurchasedItems((int) items.stream().filter(item -> !Boolean.TRUE.equals(item.getExcluded())
                && Boolean.TRUE.equals(item.getPurchased())).count());
        dto.setVersion(entity.getVersion());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setItems(items);
        return dto;
    }

    private GroceryListItemResponseDto toItemDto(GroceryListItemEntity entity) {
        GroceryListItemResponseDto dto = new GroceryListItemResponseDto();
        dto.setId(entity.getId());
        dto.setFoodItemId(entity.getFoodItem() == null ? null : entity.getFoodItem().getId());
        dto.setDisplayName(entity.getDisplayName());
        dto.setCategory(entity.getCategory());
        dto.setSource(entity.getSource());
        dto.setDisplayQuantity(entity.getDisplayQuantity());
        dto.setDisplayUnit(entity.getDisplayUnit());
        dto.setNormalizedGrams(entity.getNormalizedGrams());
        dto.setPlannedUses(entity.getPlannedUses());
        dto.setPurchased(entity.getPurchased());
        dto.setExcluded(entity.getExcluded());
        dto.setQuantityOverridden(entity.getQuantityOverridden());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private GroceryListEntity getOwned(Long listId, UserEntity user) {
        return groceryListRepository.findByIdAndUser(listId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Grocery list not found"));
    }

    private GroceryListEntity getOwnedForUpdate(Long listId, UserEntity user) {
        return groceryListRepository.findOwnedForUpdate(listId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Grocery list not found"));
    }

    private GroceryListItemEntity getOwnedItemForUpdate(Long itemId, Long listId, UserEntity user) {
        return groceryListItemRepository.findOwnedForUpdate(itemId, listId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Grocery list item not found"));
    }

    private void assertAccess(String email) {
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.GROCERY_LIST);
    }

    private void assertEditable(GroceryListEntity list) {
        if (list.getStatus() != GroceryListStatus.ACTIVE) {
            throw new RequestConflictException("Only an active grocery list can be edited");
        }
    }

    private void assertItemCapacity(GroceryListEntity list) {
        if (groceryListItemRepository.countByGroceryList(list) >= GroceryListLimits.MAX_ITEMS_PER_LIST) {
            throw new IllegalArgumentException("Grocery list item limit exceeded");
        }
    }

    private void assertVersion(GroceryListItemEntity item, Long expectedVersion) {
        if (expectedVersion == null || !expectedVersion.equals(item.getVersion())) {
            throw new RequestConflictException("Grocery list item was updated by another request");
        }
    }

    private boolean positive(Double value) {
        return value != null && value > 0;
    }
}
