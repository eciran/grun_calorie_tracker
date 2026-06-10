package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiRecipeDraftConfirmRequestDto;
import com.grun.calorietracker.dto.AiRecipeIngredientSuggestionDto;
import com.grun.calorietracker.dto.AiRecipeDraftRequestDto;
import com.grun.calorietracker.dto.AiRecipeDraftResponseDto;
import com.grun.calorietracker.dto.RecipeIngredientRequestDto;
import com.grun.calorietracker.dto.RecipeDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AiMealDraftProviderClient;
import com.grun.calorietracker.service.AiProviderConfigurationValidator;
import com.grun.calorietracker.service.AiRecipeDraftService;
import com.grun.calorietracker.service.RecipeService;
import com.grun.calorietracker.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiRecipeDraftServiceImpl implements AiRecipeDraftService {

    private static final int MAX_SUGGESTED_INGREDIENTS = 30;
    private static final int MAX_RECIPE_INGREDIENTS = 50;
    private static final List<String> ALLOWED_MEAL_TYPES = List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK");

    private final AiProperties properties;
    private final List<AiMealDraftProviderClient> providerClients;
    private final AiRequestHistoryRepository aiRequestHistoryRepository;
    private final UserRepository userRepository;
    private final FoodItemRepository foodItemRepository;
    private final SubscriptionService subscriptionService;
    private final RecipeService recipeService;
    private final ObjectMapper objectMapper;
    private final AiProviderConfigurationValidator providerConfigurationValidator;

    @Override
    @Transactional
    public AiRecipeDraftResponseDto createRecipeDraft(String email, AiRecipeDraftRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("AI recipe draft request is required.");
        }
        providerConfigurationValidator.validateConfiguredForDraft();
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.AI_RECIPE_GENERATION);
        UserEntity user = getUser(email);

        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setUser(user);
        history.setRequestType(AiRequestType.AI_RECIPE_GENERATION);
        history.setProvider(properties.getProvider());
        history.setModel(properties.getModel());
        history.setInputPayload(writeJson(toPrivacySafeInputPayload(request)));
        history.setCreatedAt(LocalDateTime.now());
        history.setQuotaConsumed(false);

        long startedAt = System.nanoTime();
        try {
            AiRecipeDraftResponseDto response = normalize(activeProvider().createRecipeDraft(request), user);
            SubscriptionDto quota = subscriptionService.consumeAiQuota(email);
            response.setAiRemainingThisPeriod(quota.getAiRemainingThisPeriod());

            history.setStatus(AiRequestStatus.DRAFT_CREATED);
            history.setOutputPayload(writeJson(response));
            history.setQuotaConsumed(true);
            history.setQuotaConsumedAmount(1);
            history.setLatencyMs(elapsedMs(startedAt));
            AiRequestHistoryEntity saved = aiRequestHistoryRepository.save(history);
            response.setRequestId(saved.getId());
            return response;
        } catch (RuntimeException ex) {
            history.setStatus(AiRequestStatus.FAILED);
            history.setErrorMessage(ex.getMessage());
            history.setLatencyMs(elapsedMs(startedAt));
            aiRequestHistoryRepository.save(history);
            throw ex;
        }
    }

    @Override
    @Transactional
    public RecipeDto confirmRecipeDraft(String email, Long requestId, AiRecipeDraftConfirmRequestDto request) {
        if (request == null || request.getRecipe() == null) {
            throw new IllegalArgumentException("Final recipe payload is required.");
        }
        UserEntity user = getUser(email);
        AiRequestHistoryEntity history = aiRequestHistoryRepository.findByIdAndUser(requestId, user)
                .orElseThrow(() -> new IllegalArgumentException("AI recipe draft was not found."));
        if (history.getRequestType() != AiRequestType.AI_RECIPE_GENERATION) {
            throw new IllegalArgumentException("AI request is not a recipe draft.");
        }
        if (history.getStatus() != AiRequestStatus.DRAFT_CREATED) {
            throw new IllegalArgumentException("AI recipe draft is not open for confirmation.");
        }
        RecipeDto recipe = recipeService.createRecipe(email, request.getRecipe());
        history.setStatus(AiRequestStatus.CONFIRMED);
        history.setConfirmationPayload(writeJson(Map.of("recipeId", recipe.getId())));
        history.setConfirmedAt(LocalDateTime.now());
        aiRequestHistoryRepository.save(history);
        return recipe;
    }

    private AiRecipeDraftResponseDto normalize(AiRecipeDraftResponseDto response, UserEntity user) {
        if (response == null) {
            throw new IllegalArgumentException("AI recipe provider returned an empty response.");
        }
        response.setRequestType(AiRequestType.AI_RECIPE_GENERATION);
        response.setProvider(properties.getProvider());
        response.setModel(properties.getModel());
        response.setStatus(AiRequestStatus.DRAFT_CREATED);
        response.setReviewRequired(true);
        if (response.getSuggestedRecipe() == null) {
            throw new IllegalArgumentException("AI recipe provider returned no suggested recipe.");
        }
        validateSuggestedRecipe(response);
        matchSuggestedIngredients(response, user);
        if (response.getWarnings() == null) {
            response.setWarnings(List.of());
        }
        return response;
    }

    private void validateSuggestedRecipe(AiRecipeDraftResponseDto response) {
        var recipe = response.getSuggestedRecipe();
        if (recipe.getName() == null || recipe.getName().isBlank()) {
            throw new IllegalArgumentException("AI recipe provider returned a recipe without a name.");
        }
        if (recipe.getName().trim().length() > 160) {
            throw new IllegalArgumentException("AI recipe provider returned a recipe name that is too long.");
        }
        if (recipe.getDescription() != null && recipe.getDescription().length() > 1000) {
            throw new IllegalArgumentException("AI recipe provider returned a recipe description that is too long.");
        }
        if (recipe.getMealType() != null
                && !recipe.getMealType().isBlank()
                && !ALLOWED_MEAL_TYPES.contains(recipe.getMealType().trim().toUpperCase())) {
            throw new IllegalArgumentException("AI recipe provider returned an invalid meal type.");
        }
        if (recipe.getServingCount() != null && (recipe.getServingCount() <= 0 || recipe.getServingCount() > 20)) {
            throw new IllegalArgumentException("AI recipe provider returned an invalid serving count.");
        }
        if (recipe.getTotalYieldGrams() != null && recipe.getTotalYieldGrams() <= 0) {
            throw new IllegalArgumentException("AI recipe provider returned an invalid total yield.");
        }
        if (recipe.getDefaultServingGrams() != null && recipe.getDefaultServingGrams() <= 0) {
            throw new IllegalArgumentException("AI recipe provider returned an invalid default serving amount.");
        }
        if (recipe.getIngredients() != null && recipe.getIngredients().size() > MAX_RECIPE_INGREDIENTS) {
            throw new IllegalArgumentException("AI recipe provider returned too many recipe ingredients.");
        }
        if (response.getSuggestedIngredients() != null && response.getSuggestedIngredients().size() > MAX_SUGGESTED_INGREDIENTS) {
            throw new IllegalArgumentException("AI recipe provider returned too many ingredient suggestions.");
        }
        if ((recipe.getIngredients() == null || recipe.getIngredients().isEmpty())
                && (response.getSuggestedIngredients() == null || response.getSuggestedIngredients().isEmpty())) {
            throw new IllegalArgumentException("AI recipe provider returned no ingredients to review.");
        }
        if (recipe.getIngredients() != null) {
            for (RecipeIngredientRequestDto ingredient : recipe.getIngredients()) {
                if (ingredient.getFoodItemId() == null || ingredient.getFoodItemId() <= 0) {
                    throw new IllegalArgumentException("AI recipe provider returned an invalid food item id.");
                }
                if (ingredient.getPortionSize() == null || ingredient.getPortionSize() <= 0) {
                    throw new IllegalArgumentException("AI recipe provider returned an invalid ingredient portion.");
                }
            }
        }
    }

    private void matchSuggestedIngredients(AiRecipeDraftResponseDto response, UserEntity user) {
        if (response.getSuggestedIngredients() == null) {
            response.setSuggestedIngredients(List.of());
            return;
        }
        for (AiRecipeIngredientSuggestionDto ingredient : response.getSuggestedIngredients()) {
            normalizeIngredientSuggestion(ingredient);
            if ("INVALID_PORTION".equals(ingredient.getMatchReason())) {
                continue;
            }
            if (ingredient.getMatchedFoodItemId() != null) {
                ingredient.setReviewRequired(requiresReview(ingredient.getConfidence()));
                ingredient.setMatchReason(Boolean.TRUE.equals(ingredient.getReviewRequired()) ? "LOW_CONFIDENCE_PROVIDER_MATCH" : "PROVIDER_MATCHED");
                continue;
            }
            if (ingredient.getName() == null || ingredient.getName().isBlank()) {
                ingredient.setReviewRequired(true);
                ingredient.setMatchReason("MISSING_NAME");
                continue;
            }
            List<FoodItemEntity> candidates = foodItemRepository.findVisibleAiMatchCandidates(
                    ingredient.getName().trim(),
                    user,
                    PageRequest.of(0, 1)
            );
            if (candidates.isEmpty()) {
                ingredient.setReviewRequired(true);
                ingredient.setMatchReason("NO_CATALOG_MATCH");
                continue;
            }
            FoodItemEntity candidate = candidates.get(0);
            ingredient.setMatchedFoodItemId(candidate.getId());
            if (candidate.getVerificationStatus() == VerificationStatus.VERIFIED && !requiresReview(ingredient.getConfidence())) {
                ingredient.setReviewRequired(false);
                ingredient.setMatchReason("VERIFIED_CATALOG_MATCH");
            } else {
                ingredient.setReviewRequired(true);
                ingredient.setMatchReason("CATALOG_MATCH_REQUIRES_REVIEW");
            }
        }
    }

    private void normalizeIngredientSuggestion(AiRecipeIngredientSuggestionDto ingredient) {
        if (ingredient.getPortionUnit() == null) {
            ingredient.setPortionUnit(FoodPortionUnit.GRAM);
        }
        if (ingredient.getPortionSize() == null || ingredient.getPortionSize() <= 0) {
            ingredient.setReviewRequired(true);
            ingredient.setMatchReason("INVALID_PORTION");
        }
    }

    private boolean requiresReview(Double confidence) {
        return confidence == null || confidence < 0.75;
    }

    private AiMealDraftProviderClient activeProvider() {
        Map<AiProvider, AiMealDraftProviderClient> clients = new EnumMap<>(AiProvider.class);
        for (AiMealDraftProviderClient client : providerClients) {
            clients.put(client.provider(), client);
        }
        AiMealDraftProviderClient client = clients.get(properties.getProvider());
        if (client == null) {
            throw new IllegalArgumentException("AI provider is not configured: " + properties.getProvider());
        }
        return client;
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private Map<String, Object> toPrivacySafeInputPayload(AiRecipeDraftRequestDto request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestType", AiRequestType.AI_RECIPE_GENERATION);
        payload.put("promptLength", request == null || request.getPrompt() == null ? 0 : request.getPrompt().length());
        payload.put("mealType", request == null ? null : request.getMealType());
        payload.put("marketRegion", request == null ? null : request.getMarketRegion());
        payload.put("language", request == null ? null : request.getLanguage());
        payload.put("servingCount", request == null ? null : request.getServingCount());
        payload.put("targetCaloriesPerServing", request == null ? null : request.getTargetCaloriesPerServing());
        payload.put("dietaryPreferenceCount", request == null || request.getDietaryPreferences() == null ? 0 : request.getDietaryPreferences().size());
        payload.put("excludedIngredientCount", request == null || request.getExcludedIngredients() == null ? 0 : request.getExcludedIngredients().size());
        payload.put("availableIngredientCount", request == null || request.getAvailableIngredients() == null ? 0 : request.getAvailableIngredients().size());
        return payload;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("AI recipe payload could not be serialized.");
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
