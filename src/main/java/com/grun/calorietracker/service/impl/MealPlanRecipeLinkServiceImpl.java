package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.MealPlanNutritionSnapshotDto;
import com.grun.calorietracker.dto.MealPlanRecipeCandidateDto;
import com.grun.calorietracker.dto.MealPlanRecipeLinkDto;
import com.grun.calorietracker.entity.MealPlanItemEntity;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.MealPlanItemLinkState;
import com.grun.calorietracker.enums.MealPlanItemType;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.MealPlanItemRepository;
import com.grun.calorietracker.repository.RecipeRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.MealPlanRecipeLinkService;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MealPlanRecipeLinkServiceImpl implements MealPlanRecipeLinkService {

    private static final int MAX_CANDIDATES = 5;
    private static final int SEARCH_WINDOW = 50;
    private static final Set<String> STOP_WORDS = Set.of(
            "and", "with", "the", "for", "from", "of", "a", "an", "ve", "ile", "bir"
    );

    private final MealPlanItemRepository mealPlanItemRepository;
    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<MealPlanRecipeCandidateDto> findCandidates(String email, Long planId, Long itemId, int limit) {
        UserEntity user = getUser(email);
        MealPlanItemEntity item = getOwnedItem(planId, itemId, user);
        validateSnapshotItem(item);
        Set<String> searchTokens = tokens(item.getSnapshotName());
        if (searchTokens.isEmpty()) {
            return List.of();
        }

        return recipeRepository.findAll(candidateSpecification(user, searchTokens), PageRequest.of(0, SEARCH_WINDOW))
                .getContent().stream()
                .map(recipe -> toCandidate(recipe, user, item.getSnapshotName()))
                .filter(candidate -> candidate.getMatchScore() > 0)
                .sorted(Comparator.comparingInt(MealPlanRecipeCandidateDto::getMatchScore).reversed()
                        .thenComparing(MealPlanRecipeCandidateDto::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(Math.min(Math.max(limit, 1), MAX_CANDIDATES))
                .toList();
    }

    @Override
    @Transactional
    public MealPlanRecipeLinkDto linkRecipe(String email, Long planId, Long itemId, Long recipeId) {
        UserEntity user = getUser(email);
        MealPlanItemEntity item = mealPlanItemRepository.findOwnedForUpdate(itemId, planId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Meal plan item not found"));
        validateSnapshotItem(item);
        if (item.getFoodItem() != null) {
            throw new RequestConflictException("Meal plan item is already linked to a food item.");
        }
        RecipeEntity recipe = recipeRepository.findById(recipeId)
                .filter(candidate -> isAccessible(candidate, user))
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

        item.setRecipe(recipe);
        item.setLinkState(MealPlanItemLinkState.USER_CONFIRMED);
        mealPlanItemRepository.save(item);
        return toLinkDto(item, true);
    }

    @Override
    @Transactional
    public MealPlanRecipeLinkDto unlinkRecipe(String email, Long planId, Long itemId) {
        UserEntity user = getUser(email);
        MealPlanItemEntity item = mealPlanItemRepository.findOwnedForUpdate(itemId, planId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Meal plan item not found"));
        validateSnapshotItem(item);
        item.setRecipe(null);
        item.setLinkState(MealPlanItemLinkState.NONE);
        mealPlanItemRepository.save(item);
        return toLinkDto(item, false);
    }

    private Specification<RecipeEntity> candidateSpecification(UserEntity user, Set<String> searchTokens) {
        return (root, query, cb) -> {
            List<Predicate> namePredicates = searchTokens.stream()
                    .map(token -> cb.like(cb.lower(root.get("name")), "%" + token + "%"))
                    .toList();
            Predicate owned = cb.equal(root.get("ownerUser"), user);
            Predicate publicVerified = cb.and(
                    cb.equal(root.get("visibility"), RecipeVisibility.PUBLIC_ADMIN),
                    cb.equal(root.get("verificationStatus"), VerificationStatus.VERIFIED),
                    publicMarketPredicate(root, cb, user.getMarketRegion()),
                    publicLanguagePredicate(root, cb, user)
            );
            return cb.and(
                    cb.isFalse(root.get("archived")),
                    cb.or(owned, publicVerified),
                    cb.or(namePredicates.toArray(Predicate[]::new))
            );
        };
    }

    private Predicate publicMarketPredicate(Root<RecipeEntity> root, CriteriaBuilder cb, MarketRegion marketRegion) {
        if (marketRegion == null || marketRegion == MarketRegion.GLOBAL) {
            return cb.equal(root.get("marketRegion"), MarketRegion.GLOBAL);
        }
        return root.get("marketRegion").in(marketRegion, MarketRegion.GLOBAL);
    }

    private Predicate publicLanguagePredicate(Root<RecipeEntity> root, CriteriaBuilder cb, UserEntity user) {
        if (user.getPreferredLanguage() == null) {
            return cb.conjunction();
        }
        String language = user.getPreferredLanguage().name().toLowerCase(Locale.ROOT);
        return cb.or(
                cb.isNull(root.get("language")),
                cb.equal(cb.lower(root.get("language")), language)
        );
    }

    private MealPlanRecipeCandidateDto toCandidate(RecipeEntity recipe, UserEntity user, String snapshotName) {
        Set<String> snapshotTokens = tokens(snapshotName);
        Set<String> recipeTokens = tokens(recipe.getName());
        Set<String> intersection = new LinkedHashSet<>(snapshotTokens);
        intersection.retainAll(recipeTokens);
        Set<String> union = new LinkedHashSet<>(snapshotTokens);
        union.addAll(recipeTokens);
        boolean exact = normalizedName(snapshotName) != null
                && normalizedName(snapshotName).equals(normalizedName(recipe.getName()));
        int overlapScore = union.isEmpty() ? 0 : (int) Math.round(80.0 * intersection.size() / union.size());

        MealPlanRecipeCandidateDto dto = new MealPlanRecipeCandidateDto();
        dto.setRecipeId(recipe.getId());
        dto.setName(FoodProductNormalizationRules.normalizeProductDisplayName(recipe.getName()));
        dto.setDescription(recipe.getDescription());
        dto.setMealType(recipe.getMealType());
        dto.setImageUrl(recipe.getImageUrl());
        dto.setMarketRegion(recipe.getMarketRegion());
        dto.setLanguage(recipe.getLanguage());
        dto.setOwnedByUser(recipe.getOwnerUser() != null && recipe.getOwnerUser().getId().equals(user.getId()));
        dto.setExactNameMatch(exact);
        dto.setMatchScore(exact ? 100 : overlapScore);
        dto.setMatchReasons(exact ? List.of("EXACT_NAME") : List.of("NAME_TOKEN_OVERLAP"));
        dto.setServingCount(recipe.getServingCount());
        dto.setDefaultServingGrams(recipe.getDefaultServingGrams());
        dto.setPerServingNutrition(perServingNutrition(recipe));
        return dto;
    }

    private MealPlanNutritionSnapshotDto perServingNutrition(RecipeEntity recipe) {
        double divisor = recipe.getServingCount() == null || recipe.getServingCount() <= 0 ? 1.0 : recipe.getServingCount();
        MealPlanNutritionSnapshotDto dto = new MealPlanNutritionSnapshotDto();
        dto.setCalories(divide(recipe.getSnapshotCalories(), divisor));
        dto.setProtein(divide(recipe.getSnapshotProtein(), divisor));
        dto.setCarbs(divide(recipe.getSnapshotCarbs(), divisor));
        dto.setFat(divide(recipe.getSnapshotFat(), divisor));
        dto.setFiber(divide(recipe.getSnapshotFiber(), divisor));
        dto.setSugar(divide(recipe.getSnapshotSugar(), divisor));
        dto.setSaturatedFat(divide(recipe.getSnapshotSaturatedFat(), divisor));
        dto.setSodium(divide(recipe.getSnapshotSodium(), divisor));
        dto.setPotassium(divide(recipe.getSnapshotPotassium(), divisor));
        dto.setCholesterol(divide(recipe.getSnapshotCholesterol(), divisor));
        dto.setCalcium(divide(recipe.getSnapshotCalcium(), divisor));
        dto.setIron(divide(recipe.getSnapshotIron(), divisor));
        dto.setMagnesium(divide(recipe.getSnapshotMagnesium(), divisor));
        dto.setZinc(divide(recipe.getSnapshotZinc(), divisor));
        dto.setVitaminA(divide(recipe.getSnapshotVitaminA(), divisor));
        dto.setVitaminC(divide(recipe.getSnapshotVitaminC(), divisor));
        dto.setVitaminD(divide(recipe.getSnapshotVitaminD(), divisor));
        dto.setVitaminE(divide(recipe.getSnapshotVitaminE(), divisor));
        dto.setVitaminB12(divide(recipe.getSnapshotVitaminB12(), divisor));
        return dto;
    }

    private Double divide(Double value, double divisor) {
        return value == null ? null : Math.round((value / divisor) * 100.0) / 100.0;
    }

    private boolean isAccessible(RecipeEntity recipe, UserEntity user) {
        if (Boolean.TRUE.equals(recipe.getArchived())) {
            return false;
        }
        if (recipe.getOwnerUser() != null && recipe.getOwnerUser().getId().equals(user.getId())) {
            return true;
        }
        if (recipe.getVisibility() != RecipeVisibility.PUBLIC_ADMIN
                || recipe.getVerificationStatus() != VerificationStatus.VERIFIED) {
            return false;
        }
        boolean marketMatches = recipe.getMarketRegion() == MarketRegion.GLOBAL
                || recipe.getMarketRegion() != null && recipe.getMarketRegion() == user.getMarketRegion();
        boolean languageMatches = recipe.getLanguage() == null || recipe.getLanguage().isBlank()
                || user.getPreferredLanguage() != null
                && recipe.getLanguage().equalsIgnoreCase(user.getPreferredLanguage().name());
        return marketMatches && languageMatches;
    }

    private void validateSnapshotItem(MealPlanItemEntity item) {
        if (item.getItemType() != MealPlanItemType.AI_SNAPSHOT) {
            throw new RequestConflictException("Recipe enrichment is available only for snapshot meal-plan items.");
        }
    }

    private MealPlanItemEntity getOwnedItem(Long planId, Long itemId, UserEntity user) {
        return mealPlanItemRepository.findOwned(itemId, planId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Meal plan item not found"));
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Set<String> tokens(String value) {
        String normalized = normalizedName(value);
        if (normalized == null) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        Arrays.stream(normalized.split("[^a-z0-9]+"))
                .filter(token -> token.length() >= 2)
                .filter(token -> !STOP_WORDS.contains(token))
                .forEach(tokens::add);
        return tokens;
    }

    private String normalizedName(String value) {
        String normalized = FoodProductNormalizationRules.normalizeSearchAlias(value);
        return normalized == null ? null : normalized.replaceAll("[^a-z0-9]+", " ").trim();
    }

    private MealPlanRecipeLinkDto toLinkDto(MealPlanItemEntity item, boolean available) {
        MealPlanRecipeLinkDto dto = new MealPlanRecipeLinkDto();
        dto.setMealPlanId(item.getMealPlan().getId());
        dto.setMealPlanItemId(item.getId());
        dto.setRecipeId(item.getRecipe() == null ? null : item.getRecipe().getId());
        dto.setRecipeName(item.getRecipe() == null ? null
                : FoodProductNormalizationRules.normalizeProductDisplayName(item.getRecipe().getName()));
        dto.setLinkState(item.getLinkState());
        dto.setRecipeNavigationAvailable(available && item.getRecipe() != null);
        dto.setRecipeOwnedByUser(item.getRecipe() != null && item.getRecipe().getOwnerUser() != null
                && item.getRecipe().getOwnerUser().getId().equals(item.getMealPlan().getUser().getId()));
        return dto;
    }
}