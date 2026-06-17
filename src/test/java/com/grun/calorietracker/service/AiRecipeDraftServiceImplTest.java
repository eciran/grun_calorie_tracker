package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiRecipeDraftConfirmRequestDto;
import com.grun.calorietracker.dto.AiRecipeIngredientSuggestionDto;
import com.grun.calorietracker.dto.AiRecipeDraftRequestDto;
import com.grun.calorietracker.dto.AiRecipeDraftResponseDto;
import com.grun.calorietracker.dto.RecipeDto;
import com.grun.calorietracker.dto.RecipeIngredientRequestDto;
import com.grun.calorietracker.dto.RecipeRequestDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.AiProviderConfigurationValidatorImpl;
import com.grun.calorietracker.service.impl.AiRecipeDraftServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiRecipeDraftServiceImplTest {

    private AiProperties properties;
    private AiMealDraftProviderClient providerClient;
    private AiRequestHistoryRepository historyRepository;
    private UserRepository userRepository;
    private FoodItemRepository foodItemRepository;
    private SubscriptionService subscriptionService;
    private RecipeService recipeService;
    private AiRecipeDraftServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider(AiProvider.LOG);
        properties.setModel("log-draft-v1");
        providerClient = mock(AiMealDraftProviderClient.class);
        historyRepository = mock(AiRequestHistoryRepository.class);
        userRepository = mock(UserRepository.class);
        foodItemRepository = mock(FoodItemRepository.class);
        subscriptionService = mock(SubscriptionService.class);
        recipeService = mock(RecipeService.class);
        service = new AiRecipeDraftServiceImpl(
                properties,
                List.of(providerClient),
                historyRepository,
                userRepository,
                foodItemRepository,
                subscriptionService,
                recipeService,
                new ObjectMapper().findAndRegisterModules(),
                new AiProviderConfigurationValidatorImpl(properties)
        );
        user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");
    }

    @Test
    void createRecipeDraft_whenProviderDisabled_failsBeforeQuota() {
        properties.setEnabled(false);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.createRecipeDraft("user@example.com", new AiRecipeDraftRequestDto()));

        verifyNoInteractions(subscriptionService, historyRepository);
    }

    @Test
    void createRecipeDraft_createsHistoryAndConsumesQuotaAfterProviderSuccess() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(providerClient.provider()).thenReturn(AiProvider.LOG);
        when(providerClient.createRecipeDraft(any())).thenReturn(providerResponse());
        SubscriptionDto quota = new SubscriptionDto();
        quota.setAiRemainingThisPeriod(9);
        when(subscriptionService.consumeAiQuota("user@example.com")).thenReturn(quota);
        when(historyRepository.save(any(AiRequestHistoryEntity.class))).thenAnswer(invocation -> {
            AiRequestHistoryEntity entity = invocation.getArgument(0);
            entity.setId(44L);
            return entity;
        });

        AiRecipeDraftResponseDto result = service.createRecipeDraft("user@example.com", request());

        assertEquals(44L, result.getRequestId());
        assertEquals(AiRequestType.AI_RECIPE_GENERATION, result.getRequestType());
        assertEquals(9, result.getAiRemainingThisPeriod());
        assertEquals(true, result.getReviewRequired());
        verify(subscriptionService).assertFeatureAccess("user@example.com", SubscriptionFeature.AI_RECIPE_GENERATION);
        verify(subscriptionService).consumeAiQuota("user@example.com");

        ArgumentCaptor<AiRequestHistoryEntity> captor = ArgumentCaptor.forClass(AiRequestHistoryEntity.class);
        verify(historyRepository).save(captor.capture());
        assertEquals(AiRequestType.AI_RECIPE_GENERATION, captor.getValue().getRequestType());
        assertEquals(AiRequestStatus.DRAFT_CREATED, captor.getValue().getStatus());
        assertEquals(true, captor.getValue().getQuotaConsumed());
        assertFalse(captor.getValue().getInputPayload().contains("high protein chicken dinner"));
    }

    @Test
    void createRecipeDraft_whenQuotaUnavailable_doesNotCallProvider() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionService.consumeAiQuota("user@example.com"))
                .thenThrow(new IllegalArgumentException("AI quota is not available for the current subscription."));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.createRecipeDraft("user@example.com", request()));

        verify(subscriptionService).assertFeatureAccess("user@example.com", SubscriptionFeature.AI_RECIPE_GENERATION);
        verify(providerClient, org.mockito.Mockito.never()).createRecipeDraft(any());
        verify(historyRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void createRecipeDraft_whenProviderResponseInvalid_refundsQuotaAndStoresFailedHistory() {
        AiRecipeDraftResponseDto invalid = providerResponse();
        invalid.setSuggestedRecipe(null);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(providerClient.provider()).thenReturn(AiProvider.LOG);
        when(providerClient.createRecipeDraft(any())).thenReturn(invalid);
        when(subscriptionService.consumeAiQuota("user@example.com")).thenReturn(new SubscriptionDto());
        when(historyRepository.save(any(AiRequestHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.createRecipeDraft("user@example.com", request()));

        verify(subscriptionService).consumeAiQuota("user@example.com");
        verify(subscriptionService).refundConsumedAiQuota(1L, 1);
        ArgumentCaptor<AiRequestHistoryEntity> captor = ArgumentCaptor.forClass(AiRequestHistoryEntity.class);
        verify(historyRepository).save(captor.capture());
        assertEquals(AiRequestStatus.FAILED, captor.getValue().getStatus());
        assertEquals(false, captor.getValue().getQuotaConsumed());
    }

    @Test
    void createRecipeDraft_matchesSuggestedIngredientsAgainstCatalog() {
        AiRecipeDraftResponseDto providerResponse = providerResponse();
        AiRecipeIngredientSuggestionDto suggestion = new AiRecipeIngredientSuggestionDto();
        suggestion.setName("Chicken breast");
        suggestion.setPortionSize(150.0);
        suggestion.setPortionUnit(FoodPortionUnit.GRAM);
        suggestion.setConfidence(0.9);
        providerResponse.setSuggestedIngredients(List.of(suggestion));

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(providerClient.provider()).thenReturn(AiProvider.LOG);
        when(providerClient.createRecipeDraft(any())).thenReturn(providerResponse);
        when(foodItemRepository.findVisibleAiMatchCandidates(
                org.mockito.Mockito.eq("Chicken breast"),
                org.mockito.Mockito.eq(user),
                any()
        )).thenReturn(List.of(verifiedFoodItem()));
        SubscriptionDto quota = new SubscriptionDto();
        quota.setAiRemainingThisPeriod(9);
        when(subscriptionService.consumeAiQuota("user@example.com")).thenReturn(quota);
        when(historyRepository.save(any(AiRequestHistoryEntity.class))).thenAnswer(invocation -> {
            AiRequestHistoryEntity entity = invocation.getArgument(0);
            entity.setId(44L);
            return entity;
        });

        AiRecipeDraftResponseDto result = service.createRecipeDraft("user@example.com", request());

        assertEquals(12L, result.getSuggestedIngredients().get(0).getMatchedFoodItemId());
        assertEquals(false, result.getSuggestedIngredients().get(0).getReviewRequired());
        assertEquals("VERIFIED_CATALOG_MATCH", result.getSuggestedIngredients().get(0).getMatchReason());
    }

    @Test
    void confirmRecipeDraft_createsRecipeAndClosesDraft() {
        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setId(44L);
        history.setUser(user);
        history.setRequestType(AiRequestType.AI_RECIPE_GENERATION);
        history.setProvider(AiProvider.LOG);
        history.setModel("log-draft-v1");
        history.setStatus(AiRequestStatus.DRAFT_CREATED);
        history.setCreatedAt(LocalDateTime.now());
        history.setQuotaConsumed(true);

        RecipeDto createdRecipe = new RecipeDto();
        createdRecipe.setId(77L);
        createdRecipe.setName("Reviewed recipe");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(historyRepository.findByIdAndUser(44L, user)).thenReturn(Optional.of(history));
        when(recipeService.createRecipe("user@example.com", finalRecipe())).thenReturn(createdRecipe);
        when(historyRepository.save(any(AiRequestHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiRecipeDraftConfirmRequestDto request = new AiRecipeDraftConfirmRequestDto();
        request.setRecipe(finalRecipe());

        RecipeDto result = service.confirmRecipeDraft("user@example.com", 44L, request);

        assertEquals(77L, result.getId());
        verify(recipeService).createRecipe("user@example.com", finalRecipe());
        ArgumentCaptor<AiRequestHistoryEntity> captor = ArgumentCaptor.forClass(AiRequestHistoryEntity.class);
        verify(historyRepository).save(captor.capture());
        assertEquals(AiRequestStatus.CONFIRMED, captor.getValue().getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getConfirmationPayload().contains("\"recipeId\":77"));
    }

    private AiRecipeDraftRequestDto request() {
        AiRecipeDraftRequestDto request = new AiRecipeDraftRequestDto();
        request.setPrompt("high protein chicken dinner");
        request.setMealType("DINNER");
        request.setServingCount(2);
        return request;
    }

    private AiRecipeDraftResponseDto providerResponse() {
        AiRecipeDraftResponseDto response = new AiRecipeDraftResponseDto();
        response.setSuggestedRecipe(finalRecipe());
        response.setSummary("Draft recipe.");
        return response;
    }

    private RecipeRequestDto finalRecipe() {
        RecipeIngredientRequestDto ingredient = new RecipeIngredientRequestDto();
        ingredient.setFoodItemId(2L);
        ingredient.setPortionSize(150.0);
        ingredient.setPortionUnit(FoodPortionUnit.GRAM);

        RecipeRequestDto recipe = new RecipeRequestDto();
        recipe.setName("Reviewed recipe");
        recipe.setMealType("DINNER");
        recipe.setServingCount(1);
        recipe.setTotalYieldGrams(150.0);
        recipe.setDefaultServingGrams(150.0);
        recipe.setIngredients(List.of(ingredient));
        return recipe;
    }

    private FoodItemEntity verifiedFoodItem() {
        FoodItemEntity foodItem = new FoodItemEntity();
        foodItem.setId(12L);
        foodItem.setName("Chicken breast");
        foodItem.setVerificationStatus(VerificationStatus.VERIFIED);
        foodItem.setQualityScore(90);
        return foodItem;
    }
}
