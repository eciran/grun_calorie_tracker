package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiRecipeDraftConfirmRequestDto;
import com.grun.calorietracker.dto.AiMealDraftRejectRequestDto;
import com.grun.calorietracker.dto.AiRecipeIngredientSuggestionDto;
import com.grun.calorietracker.dto.AiRecipeDraftRequestDto;
import com.grun.calorietracker.dto.AiRecipeDraftResponseDto;
import com.grun.calorietracker.dto.RecipeIngredientRequestDto;
import com.grun.calorietracker.dto.RecipeNutritionDto;
import com.grun.calorietracker.dto.RecipeRequestDto;
import com.grun.calorietracker.dto.RecipeStepRequestDto;
import com.grun.calorietracker.dto.RecipeDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.dto.AiUsageMetadataCarrier;
import com.grun.calorietracker.dto.UserNutritionPreferenceDto;
import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.RecipeCategory;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AiMealDraftProviderClient;
import com.grun.calorietracker.service.AiProviderConfigurationValidator;
import com.grun.calorietracker.service.AiRecipeDraftService;
import com.grun.calorietracker.service.RecipeService;
import com.grun.calorietracker.service.SubscriptionService;
import com.grun.calorietracker.service.UserNutritionPreferenceService;
import com.grun.calorietracker.service.support.AiSafeResponseBuilder;
import com.grun.calorietracker.service.support.AiIdempotencySupport;
import com.grun.calorietracker.service.support.AiUxContractFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private final SubscriptionService subscriptionService;
    private final RecipeService recipeService;
    private final UserNutritionPreferenceService nutritionPreferenceService;
    private final ObjectMapper objectMapper;
    private final AiProviderConfigurationValidator providerConfigurationValidator;

    @Override
    public AiRecipeDraftResponseDto createRecipeDraft(
            String email, String idempotencyKey, AiRecipeDraftRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("AI recipe draft request is required.");
        }
        providerConfigurationValidator.validateConfiguredForDraft();
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.AI_RECIPE_GENERATION);
        UserEntity user = getUser(email);
        applyPersistentNutritionPreferences(email, request);
        request.setUserContext(toUserContext(user));
        String key = AiIdempotencySupport.normalizeKey(idempotencyKey);
        AiRecipeDraftResponseDto previous = existingDraft(user, key);
        if (previous != null) {
            return previous;
        }

        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setUser(user);
        history.setRequestType(AiRequestType.AI_RECIPE_GENERATION);
        history.setProvider(properties.getProvider());
        history.setModel(properties.getModel());
        history.setPromptVersion(properties.getPromptVersion());
        history.setStatus(AiRequestStatus.PROCESSING);
        history.setIdempotencyKey(key);
        history.setInputPayload(writeJson(toPrivacySafeInputPayload(request)));
        history.setCreatedAt(LocalDateTime.now());
        history.setQuotaConsumed(false);

        try {
            AiRequestHistoryEntity reserved = aiRequestHistoryRepository.save(history);
            if (reserved != null) {
                history = reserved;
            }
        } catch (DataIntegrityViolationException ex) {
            AiRecipeDraftResponseDto concurrent = existingDraft(user, key);
            if (concurrent != null) {
                return concurrent;
            }
            throw new RequestConflictException(
                    "An AI recipe-draft request with this key is already processing.");
        }

        int creditCost = subscriptionService.resolveAiCreditCost(email, SubscriptionFeature.AI_RECIPE_GENERATION);
        long startedAt = System.nanoTime();
        boolean charged = false;
        try {
            SubscriptionDto quota = subscriptionService.consumeAiQuota(email, creditCost);
            charged = true;
            AiRecipeDraftResponseDto response = normalize(activeProvider().createRecipeDraft(request), user, request);
            response.setAiRemainingThisPeriod(quota.getAiRemainingThisPeriod());
            response.setUx(AiUxContractFactory.success(
                    AiRequestStatus.DRAFT_CREATED,
                    true,
                    creditCost,
                    quota,
                    user.getPreferredLanguage()
            ));
            copyUsageMetadata(response, history);

            history.setStatus(AiRequestStatus.DRAFT_CREATED);
            history.setOutputPayload(writeJson(response));
            history.setQuotaConsumed(true);
            history.setQuotaConsumedAmount(creditCost);
            history.setLatencyMs(elapsedMs(startedAt));
            AiRequestHistoryEntity saved = aiRequestHistoryRepository.save(history);
            response.setRequestId(saved.getId());
            return response;
        } catch (RuntimeException ex) {
            boolean refunded = !charged || refundConsumedQuota(user, creditCost);
            history.setStatus(AiRequestStatus.FAILED);
            history.setErrorMessage(ex.getMessage());
            history.setOutputPayload(writeJson(AiSafeResponseBuilder.failurePayload(AiRequestType.AI_RECIPE_GENERATION, true, creditCost, !refunded, user.getPreferredLanguage())));
            history.setQuotaConsumed(!refunded);
            history.setQuotaConsumedAmount(refunded ? 0 : creditCost);
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

    @Override
    @Transactional
    public void rejectRecipeDraft(String email, Long requestId, AiMealDraftRejectRequestDto request) {
        UserEntity user = getUser(email);
        AiRequestHistoryEntity history = aiRequestHistoryRepository.findByIdAndUser(requestId, user)
                .orElseThrow(() -> new IllegalArgumentException("AI recipe draft was not found."));
        if (history.getRequestType() != AiRequestType.AI_RECIPE_GENERATION) {
            throw new IllegalArgumentException("AI request is not a recipe draft.");
        }
        if (history.getStatus() != AiRequestStatus.DRAFT_CREATED) {
            throw new IllegalArgumentException("AI recipe draft is not open for rejection.");
        }
        history.setStatus(AiRequestStatus.REJECTED);
        if (request != null) {
            history.setRejectionReason(request.getReason());
            history.setRejectionFeedback(cleanFeedback(request.getFeedback()));
        }
        history.setRejectedAt(LocalDateTime.now());
        aiRequestHistoryRepository.save(history);
    }

    private AiRecipeDraftResponseDto existingDraft(UserEntity user, String idempotencyKey) {
        return aiRequestHistoryRepository.findByUserAndRequestTypeAndIdempotencyKey(
                        user, AiRequestType.AI_RECIPE_GENERATION, idempotencyKey)
                .map(history -> AiIdempotencySupport.replayOrReject(
                        history,
                        stored -> readDraft(stored, user),
                        "AI recipe-draft"
                )).orElse(null);
    }

    private AiRecipeDraftResponseDto readDraft(AiRequestHistoryEntity history, UserEntity user) {
        try {
            AiRecipeDraftResponseDto draft = objectMapper.readValue(
                    history.getOutputPayload(), AiRecipeDraftResponseDto.class);
            draft.setRequestId(history.getId());
            draft.setStatus(history.getStatus());
            if (draft.getUx() == null) {
                int consumed = history.getQuotaConsumedAmount() == null ? 0 : history.getQuotaConsumedAmount();
                draft.setUx(AiUxContractFactory.history(
                        history.getStatus(),
                        true,
                        consumed,
                        Boolean.TRUE.equals(history.getQuotaConsumed()),
                        user.getPreferredLanguage()
                ));
            }
            return draft;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored AI recipe draft is unavailable.");
        }
    }

    private String cleanFeedback(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.trim().replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ");
        return cleaned.length() <= 1000 ? cleaned : cleaned.substring(0, 1000);
    }
    private AiRecipeDraftResponseDto normalize(AiRecipeDraftResponseDto response, UserEntity user, AiRecipeDraftRequestDto request) {
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
        validateEstimatedNutrition(response);
        normalizeSuggestedIngredientsAsSnapshot(response);
        enrichSuggestedRecipeForReview(response, request);
        normalizeQuality(response);
        if (response.getWarnings() == null) {
            response.setWarnings(List.of());
        }
        return response;
    }

    private void enrichSuggestedRecipeForReview(AiRecipeDraftResponseDto response, AiRecipeDraftRequestDto request) {
        RecipeRequestDto recipe = response.getSuggestedRecipe();
        if (recipe.getSnapshotNutritionTotal() == null) {
            recipe.setSnapshotNutritionTotal(response.getEstimatedNutritionTotal());
        }
        if ((recipe.getIngredients() == null || recipe.getIngredients().isEmpty())
                && response.getSuggestedIngredients() != null
                && !response.getSuggestedIngredients().isEmpty()) {
            recipe.setIngredients(toSnapshotIngredientRequests(response.getSuggestedIngredients()));
        }
        recipe.setCategories(mergeRecipeCategories(recipe.getCategories(), request));
        if (recipe.getAllergens() == null) {
            recipe.setAllergens(new LinkedHashSet<>());
        } else {
            recipe.setAllergens(new LinkedHashSet<>(recipe.getAllergens()));
        }
    }

    private List<RecipeIngredientRequestDto> toSnapshotIngredientRequests(List<AiRecipeIngredientSuggestionDto> suggestions) {
        List<RecipeIngredientRequestDto> ingredients = new ArrayList<>();
        for (AiRecipeIngredientSuggestionDto suggestion : suggestions) {
            if (suggestion == null || suggestion.getName() == null || suggestion.getName().isBlank()
                    || suggestion.getPortionSize() == null || suggestion.getPortionSize() <= 0) {
                continue;
            }
            RecipeIngredientRequestDto ingredient = new RecipeIngredientRequestDto();
            ingredient.setSnapshotFoodName(suggestion.getName().trim());
            ingredient.setPortionSize(suggestion.getPortionSize());
            ingredient.setPortionUnit(suggestion.getPortionUnit() == null ? FoodPortionUnit.GRAM : suggestion.getPortionUnit());
            ingredients.add(ingredient);
        }
        return ingredients;
    }

    private Set<RecipeCategory> mergeRecipeCategories(Set<RecipeCategory> existing, AiRecipeDraftRequestDto request) {
        LinkedHashSet<RecipeCategory> categories = existing == null ? new LinkedHashSet<>() : new LinkedHashSet<>(existing);
        if (request != null) {
            RecipeCategory mealCategory = mapMealTypeToCategory(request.getMealType());
            if (mealCategory != null) {
                categories.add(mealCategory);
            }
            if (request.getDietaryPreferences() != null) {
                for (String preference : request.getDietaryPreferences()) {
                    RecipeCategory category = mapDietaryPreferenceToCategory(preference);
                    if (category != null) {
                        categories.add(category);
                    }
                }
            }
            if (request.getExcludedIngredients() != null) {
                for (String exclusion : request.getExcludedIngredients()) {
                    RecipeCategory category = mapAvoidanceToCategory(exclusion);
                    if (category != null) {
                        categories.add(category);
                    }
                }
            }
        }
        return categories;
    }

    private RecipeCategory mapMealTypeToCategory(String mealType) {
        String normalized = normalizeKey(mealType);
        return switch (normalized) {
            case "BREAKFAST" -> RecipeCategory.BREAKFAST;
            case "LUNCH" -> RecipeCategory.LUNCH;
            case "DINNER" -> RecipeCategory.DINNER;
            case "SNACK" -> RecipeCategory.SNACK;
            default -> null;
        };
    }

    private RecipeCategory mapDietaryPreferenceToCategory(String value) {
        String normalized = normalizeKey(value);
        return switch (normalized) {
            case "VEGAN" -> RecipeCategory.VEGAN;
            case "VEGETARIAN" -> RecipeCategory.VEGETARIAN;
            case "HIGH_PROTEIN", "PROTEIN", "HIGHPROTEIN" -> RecipeCategory.HIGH_PROTEIN;
            case "LOW_CARB", "LOWCARB", "KETO" -> RecipeCategory.LOW_CARB;
            case "LOW_FAT", "LOWFAT" -> RecipeCategory.LOW_FAT;
            case "LOW_CALORIE", "LOWCALORIE", "CALORIE_DEFICIT" -> RecipeCategory.LOW_CALORIE;
            case "HIGH_FIBER", "HIGHFIBER" -> RecipeCategory.HIGH_FIBER;
            case "GLUTEN_FREE", "GLUTENFREE" -> RecipeCategory.GLUTEN_FREE;
            case "DAIRY_FREE", "DAIRYFREE" -> RecipeCategory.DAIRY_FREE;
            case "MEDITERRANEAN" -> RecipeCategory.MEDITERRANEAN;
            case "TURKISH" -> RecipeCategory.TURKISH;
            default -> null;
        };
    }

    private RecipeCategory mapAvoidanceToCategory(String value) {
        String normalized = normalizeKey(value);
        return switch (normalized) {
            case "GLUTEN", "WHEAT" -> RecipeCategory.GLUTEN_FREE;
            case "DAIRY", "MILK", "LACTOSE" -> RecipeCategory.DAIRY_FREE;
            default -> null;
        };
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }
    private void applyPersistentNutritionPreferences(
            String email,
            AiRecipeDraftRequestDto request
    ) {
        UserNutritionPreferenceDto persistent = nutritionPreferenceService.get(email);
        if (persistent == null) {
            return;
        }
        request.setDietaryPreferences(mergePreferences(
                persistent.getDietaryPreferences(),
                request.getDietaryPreferences(),
                32
        ));

        List<String> mandatoryExclusions = new ArrayList<>();
        if (persistent.getExcludedFoods() != null) {
            mandatoryExclusions.addAll(persistent.getExcludedFoods());
        }
        if (persistent.getAllergens() != null) {
            persistent.getAllergens().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(Enum::name)
                    .sorted()
                    .forEach(mandatoryExclusions::add);
        }
        request.setExcludedIngredients(mergePreferences(
                mandatoryExclusions,
                request.getExcludedIngredients(),
                60
        ));
    }

    private List<String> mergePreferences(
            List<String> persistent,
            List<String> requestValues,
            int maxItems
    ) {
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        addCleanPreferences(unique, persistent, maxItems);
        addCleanPreferences(unique, requestValues, maxItems);
        return new ArrayList<>(unique.values());
    }

    private void addCleanPreferences(
            LinkedHashMap<String, String> target,
            List<String> values,
            int maxItems
    ) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String cleaned = value.trim()
                    .replaceAll("[\\p{Cntrl}]", " ")
                    .replaceAll("\\s+", " ");
            if (cleaned.isBlank()) {
                continue;
            }
            if (cleaned.length() > 80) {
                throw new IllegalArgumentException(
                        "Nutrition preference values cannot exceed 80 characters."
                );
            }
            target.putIfAbsent(cleaned.toLowerCase(Locale.ROOT), cleaned);
            if (target.size() > maxItems) {
                throw new IllegalArgumentException(
                        "Too many combined nutrition preference values."
                );
            }
        }
    }
    private void normalizeQuality(AiRecipeDraftResponseDto response) {
        response.setSchemaVersion("ai_response_v3");
        if (response.getReviewReasons() == null) {
            response.setReviewReasons(List.of());
        }
        if (response.getAssumptions() == null) {
            response.setAssumptions(List.of());
        }
        if (response.getNextBestActions() == null) {
            response.setNextBestActions(List.of());
        }
        if (response.getCookingTips() == null) {
            response.setCookingTips(List.of());
        }
        if (response.getSubstitutions() == null) {
            response.setSubstitutions(List.of());
        }
        if (response.getResultType() == null || response.getResultType().isBlank()) {
            response.setResultType("AI_SNAPSHOT");
        }
        if (response.getUserMessage() == null || response.getUserMessage().isBlank()) {
            response.setUserMessage("AI prepared an editable recipe draft with estimated nutrition. Review ingredients and portions before saving.");
        }
        if (response.getProfessionalSummary() == null || response.getProfessionalSummary().isBlank()) {
            response.setProfessionalSummary(response.getSummary());
        }
        if (response.getConfidence() == null) {
            response.setConfidence(minIngredientConfidence(response));
        }
        if (response.getQualityScore() == null && response.getConfidence() != null) {
            response.setQualityScore((int) Math.round(response.getConfidence() * 100));
        }
        if (response.getEstimatedUncertainty() == null || response.getEstimatedUncertainty().isBlank()) {
            response.setEstimatedUncertainty(resolveUncertainty(response.getConfidence()));
        }
    }

    private Double minIngredientConfidence(AiRecipeDraftResponseDto response) {
        if (response.getSuggestedIngredients() == null || response.getSuggestedIngredients().isEmpty()) {
            return null;
        }
        return response.getSuggestedIngredients().stream()
                .map(AiRecipeIngredientSuggestionDto::getConfidence)
                .filter(value -> value != null)
                .min(Double::compareTo)
                .orElse(null);
    }

    private String resolveUncertainty(Double confidence) {
        if (confidence == null || confidence < 0.55) {
            return "HIGH";
        }
        if (confidence < 0.8) {
            return "MEDIUM";
        }
        return "LOW";
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
        validateCookingSteps(recipe.getCookingSteps());
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
                if (ingredient.getFoodItemId() != null && ingredient.getFoodItemId() <= 0) {
                    throw new IllegalArgumentException("AI recipe provider returned an invalid food item id.");
                }
                if (ingredient.getPortionSize() == null || ingredient.getPortionSize() <= 0) {
                    throw new IllegalArgumentException("AI recipe provider returned an invalid ingredient portion.");
                }
            }
        }
    }

    private void validateCookingSteps(List<RecipeStepRequestDto> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("AI recipe provider returned no cooking steps.");
        }
        if (steps.size() > 30) {
            throw new IllegalArgumentException("AI recipe provider returned too many cooking steps.");
        }
        for (RecipeStepRequestDto step : steps) {
            if (step == null || step.getInstruction() == null || step.getInstruction().isBlank()) {
                throw new IllegalArgumentException("AI recipe provider returned a blank cooking step.");
            }
            if (step.getInstruction().length() > 1000) {
                throw new IllegalArgumentException("AI recipe provider returned a cooking step that is too long.");
            }
        }
    }

    private void validateEstimatedNutrition(AiRecipeDraftResponseDto response) {
        validateNutrition(response.getEstimatedNutritionTotal(), "total");
        validateNutrition(response.getEstimatedNutritionPerServing(), "per serving");
        if (response.getNutritionEstimateNote() == null || response.getNutritionEstimateNote().isBlank()) {
            throw new IllegalArgumentException("AI recipe provider returned no nutrition estimate note.");
        }
    }

    private void validateNutrition(RecipeNutritionDto nutrition, String label) {
        if (nutrition == null) {
            throw new IllegalArgumentException("AI recipe provider returned no " + label + " nutrition estimate.");
        }
        validateNutritionValue(nutrition.getCalories(), label, "calories", true);
        validateNutritionValue(nutrition.getProtein(), label, "protein", true);
        validateNutritionValue(nutrition.getCarbs(), label, "carbohydrate", true);
        validateNutritionValue(nutrition.getFat(), label, "fat", true);
        validateNutritionValue(nutrition.getFiber(), label, "fiber", false);
        validateNutritionValue(nutrition.getSugar(), label, "sugar", false);
        validateNutritionValue(nutrition.getSaturatedFat(), label, "saturated fat", false);
        validateNutritionValue(nutrition.getSodium(), label, "sodium", false);
        validateNutritionValue(nutrition.getPotassium(), label, "potassium", false);
        validateNutritionValue(nutrition.getCholesterol(), label, "cholesterol", false);
        validateNutritionValue(nutrition.getCalcium(), label, "calcium", false);
        validateNutritionValue(nutrition.getIron(), label, "iron", false);
        validateNutritionValue(nutrition.getMagnesium(), label, "magnesium", false);
        validateNutritionValue(nutrition.getZinc(), label, "zinc", false);
        validateNutritionValue(nutrition.getVitaminA(), label, "vitamin A", false);
        validateNutritionValue(nutrition.getVitaminC(), label, "vitamin C", false);
        validateNutritionValue(nutrition.getVitaminD(), label, "vitamin D", false);
        validateNutritionValue(nutrition.getVitaminE(), label, "vitamin E", false);
        validateNutritionValue(nutrition.getVitaminB12(), label, "vitamin B12", false);
    }

    private void validateNutritionValue(Double value, String label, String field, boolean required) {
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("AI recipe provider returned no " + label + " " + field + " estimate.");
            }
            return;
        }
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("AI recipe provider returned invalid " + label + " " + field + " estimate.");
        }
    }

    private void normalizeSuggestedIngredientsAsSnapshot(AiRecipeDraftResponseDto response) {
        if (response.getSuggestedIngredients() == null) {
            response.setSuggestedIngredients(List.of());
            return;
        }
        for (AiRecipeIngredientSuggestionDto ingredient : response.getSuggestedIngredients()) {
            normalizeIngredientSuggestion(ingredient);
            ingredient.setMatchedFoodItemId(null);
            ingredient.setReviewRequired(true);
            if (!"INVALID_PORTION".equals(ingredient.getMatchReason())
                    && !"MISSING_NAME".equals(ingredient.getMatchReason())) {
                ingredient.setMatchReason("AI_SNAPSHOT");
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

    private Map<String, Object> toUserContext(UserEntity user) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("age", user.getAge());
        context.put("gender", user.getGender());
        context.put("heightCm", user.getHeight());
        context.put("weightKg", user.getWeight());
        context.put("bodyFatPercentage", user.getBodyFatPercentage());
        context.put("bmi", user.getBmi());
        context.put("marketRegion", user.getMarketRegion());
        context.put("preferredLanguage", user.getPreferredLanguage());
        context.put("timeZone", user.getTimeZone());
        context.put("unitPreference", user.getUnitPreference());
        return context;
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

    private void copyUsageMetadata(AiUsageMetadataCarrier response, AiRequestHistoryEntity history) {
        if (response == null || history == null) {
            return;
        }
        history.setPromptTokens(response.getPromptTokens());
        history.setCompletionTokens(response.getCompletionTokens());
        Integer totalTokens = response.getTotalTokens();
        if (totalTokens == null && (response.getPromptTokens() != null || response.getCompletionTokens() != null)) {
            totalTokens = (response.getPromptTokens() == null ? 0 : response.getPromptTokens())
                    + (response.getCompletionTokens() == null ? 0 : response.getCompletionTokens());
        }
        history.setTotalTokens(totalTokens);
        history.setEstimatedCost(response.getEstimatedCost());
        history.setCostCurrency(response.getCostCurrency());
    }
    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private boolean refundConsumedQuota(UserEntity user, int creditCost) {
        try {
            subscriptionService.refundConsumedAiQuota(user.getId(), creditCost);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}

