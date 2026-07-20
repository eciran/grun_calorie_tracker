package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.MealPlanRecipeCandidateDto;
import com.grun.calorietracker.dto.MealPlanRecipeLinkDto;
import com.grun.calorietracker.entity.MealPlanEntity;
import com.grun.calorietracker.entity.MealPlanItemEntity;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.MealPlanItemLinkState;
import com.grun.calorietracker.enums.MealPlanItemType;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.MealPlanItemRepository;
import com.grun.calorietracker.repository.RecipeRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.MealPlanRecipeLinkServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MealPlanRecipeLinkServiceImplTest {

    private final MealPlanItemRepository itemRepository = mock(MealPlanItemRepository.class);
    private final RecipeRepository recipeRepository = mock(RecipeRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MealPlanRecipeLinkServiceImpl service = new MealPlanRecipeLinkServiceImpl(
            itemRepository, recipeRepository, userRepository);

    @Test
    void findCandidates_returnsRankedOwnedOrVerifiedRecipesWithoutMutation() {
        UserEntity user = user();
        MealPlanItemEntity item = snapshotItem(user);
        RecipeEntity exact = publicRecipe(20L, "chicken rice bowl", MarketRegion.UK_IE);
        exact.setSnapshotCalories(800.0);
        exact.setSnapshotProtein(60.0);
        exact.setServingCount(2);

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(itemRepository.findOwned(10L, 5L, user)).thenReturn(Optional.of(item));
        when(recipeRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(exact)));

        List<MealPlanRecipeCandidateDto> result = service.findCandidates(user.getEmail(), 5L, 10L, 3);

        assertEquals(1, result.size());
        assertEquals(100, result.get(0).getMatchScore());
        assertTrue(result.get(0).isExactNameMatch());
        assertEquals(400.0, result.get(0).getPerServingNutrition().getCalories());
        assertNull(item.getRecipe());
        assertEquals(520.0, item.getSnapshotCalories());
    }

    @Test
    void linkRecipe_whenUserConfirmsAccessibleRecipe_preservesSnapshot() {
        UserEntity user = user();
        MealPlanItemEntity item = snapshotItem(user);
        RecipeEntity recipe = publicRecipe(20L, "Chicken Rice Bowl", MarketRegion.UK_IE);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(itemRepository.findOwnedForUpdate(10L, 5L, user)).thenReturn(Optional.of(item));
        when(recipeRepository.findById(20L)).thenReturn(Optional.of(recipe));

        MealPlanRecipeLinkDto result = service.linkRecipe(user.getEmail(), 5L, 10L, 20L);

        assertSame(recipe, item.getRecipe());
        assertEquals(MealPlanItemLinkState.USER_CONFIRMED, item.getLinkState());
        assertEquals(520.0, item.getSnapshotCalories());
        assertEquals("Keep this snapshot", item.getSnapshotDescription());
        assertTrue(result.isRecipeNavigationAvailable());
        verify(itemRepository).save(item);
    }

    @Test
    void linkRecipe_whenPublicRecipeBelongsToAnotherMarket_rejects() {
        UserEntity user = user();
        MealPlanItemEntity item = snapshotItem(user);
        RecipeEntity recipe = publicRecipe(20L, "Chicken Rice Bowl", MarketRegion.TR);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(itemRepository.findOwnedForUpdate(10L, 5L, user)).thenReturn(Optional.of(item));
        when(recipeRepository.findById(20L)).thenReturn(Optional.of(recipe));

        assertThrows(ResourceNotFoundException.class,
                () -> service.linkRecipe(user.getEmail(), 5L, 10L, 20L));
        assertNull(item.getRecipe());
    }

    @Test
    void unlinkRecipe_removesOnlyNavigationLinkAndPreservesSnapshot() {
        UserEntity user = user();
        MealPlanItemEntity item = snapshotItem(user);
        item.setRecipe(publicRecipe(20L, "Chicken Rice Bowl", MarketRegion.UK_IE));
        item.setLinkState(MealPlanItemLinkState.USER_CONFIRMED);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(itemRepository.findOwnedForUpdate(10L, 5L, user)).thenReturn(Optional.of(item));

        MealPlanRecipeLinkDto result = service.unlinkRecipe(user.getEmail(), 5L, 10L);

        assertNull(item.getRecipe());
        assertEquals(MealPlanItemLinkState.NONE, item.getLinkState());
        assertEquals(520.0, item.getSnapshotCalories());
        assertEquals("Keep this snapshot", item.getSnapshotDescription());
        assertFalse(result.isRecipeNavigationAvailable());
        verify(itemRepository).save(item);
    }

    @Test
    void linkRecipe_whenItemIsNotSnapshot_rejectsWithoutMutation() {
        UserEntity user = user();
        MealPlanItemEntity item = snapshotItem(user);
        item.setItemType(MealPlanItemType.FOOD_ITEM);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(itemRepository.findOwnedForUpdate(10L, 5L, user)).thenReturn(Optional.of(item));

        assertThrows(RequestConflictException.class,
                () -> service.linkRecipe(user.getEmail(), 5L, 10L, 20L));
        assertNull(item.getRecipe());
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@test.com");
        user.setMarketRegion(MarketRegion.UK_IE);
        user.setPreferredLanguage(PreferredLanguage.EN);
        return user;
    }

    private MealPlanItemEntity snapshotItem(UserEntity user) {
        MealPlanEntity plan = new MealPlanEntity();
        plan.setId(5L);
        plan.setUser(user);
        MealPlanItemEntity item = new MealPlanItemEntity();
        item.setId(10L);
        item.setMealPlan(plan);
        item.setItemType(MealPlanItemType.AI_SNAPSHOT);
        item.setSnapshotName("Chicken Rice Bowl");
        item.setSnapshotDescription("Keep this snapshot");
        item.setSnapshotCalories(520.0);
        item.setSnapshotProtein(48.0);
        item.setLinkState(MealPlanItemLinkState.NONE);
        return item;
    }

    private RecipeEntity publicRecipe(Long id, String name, MarketRegion marketRegion) {
        RecipeEntity recipe = new RecipeEntity();
        recipe.setId(id);
        recipe.setName(name);
        recipe.setMarketRegion(marketRegion);
        recipe.setLanguage("en");
        recipe.setVisibility(RecipeVisibility.PUBLIC_ADMIN);
        recipe.setVerificationStatus(VerificationStatus.VERIFIED);
        recipe.setArchived(false);
        return recipe;
    }
}