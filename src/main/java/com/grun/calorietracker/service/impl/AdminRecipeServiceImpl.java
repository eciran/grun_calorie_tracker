package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminRecipeDto;
import com.grun.calorietracker.dto.AdminRecipePageDto;
import com.grun.calorietracker.dto.AdminRecipeReviewRequestDto;
import com.grun.calorietracker.dto.RecipeIngredientDto;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.RecipeIngredientEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.RecipeRepository;
import com.grun.calorietracker.repository.RecipeUserInteractionRepository;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.AdminRecipeService;
import com.grun.calorietracker.service.support.FoodPortionCalculator;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdminRecipeServiceImpl implements AdminRecipeService {

    private final RecipeRepository recipeRepository;
    private final RecipeUserInteractionRepository recipeUserInteractionRepository;
    private final AdminAuditService adminAuditService;

    @Override
    @Transactional(readOnly = true)
    public AdminRecipePageDto listRecipes(String query,
                                          VerificationStatus verificationStatus,
                                          RecipeVisibility visibility,
                                          Boolean archived,
                                          String ownerEmail,
                                          String mealType,
                                          MarketRegion marketRegion,
                                          ImageStatus imageStatus,
                                          ImageSource imageSource,
                                          int page,
                                          int size) {
        Page<RecipeEntity> recipes = recipeRepository.findAll(
                buildSpecification(query, verificationStatus, visibility, archived, ownerEmail, mealType, marketRegion, imageStatus, imageSource),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "updatedAt"))
        );
        AdminRecipePageDto dto = new AdminRecipePageDto();
        dto.setContent(recipes.getContent().stream().map(this::toDto).toList());
        dto.setPage(recipes.getNumber());
        dto.setSize(recipes.getSize());
        dto.setTotalElements(recipes.getTotalElements());
        dto.setTotalPages(recipes.getTotalPages());
        dto.setFirst(recipes.isFirst());
        dto.setLast(recipes.isLast());
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminRecipeDto getRecipe(Long id) {
        return toDto(findRecipe(id));
    }

    @Override
    @Transactional
    public AdminRecipeDto updateRecipeReview(Long id, AdminRecipeReviewRequestDto request, String adminEmail) {
        if (request == null) {
            throw new IllegalArgumentException("Recipe review request must not be empty.");
        }
        RecipeEntity recipe = findRecipe(id);
        Map<String, Object> before = auditState(recipe);
        boolean changed = false;
        if (request.getVerificationStatus() != null
                && !Objects.equals(recipe.getVerificationStatus(), request.getVerificationStatus())) {
            recipe.setVerificationStatus(request.getVerificationStatus());
            changed = true;
        }
        if (request.getVisibility() != null && !Objects.equals(recipe.getVisibility(), request.getVisibility())) {
            recipe.setVisibility(request.getVisibility());
            changed = true;
        }
        if (request.getArchived() != null && !Objects.equals(recipe.getArchived(), request.getArchived())) {
            recipe.setArchived(request.getArchived());
            changed = true;
        }
        String imageUrl = trimToNull(request.getImageUrl());
        if (request.getImageUrl() != null && !Objects.equals(recipe.getImageUrl(), imageUrl)) {
            recipe.setImageUrl(imageUrl);
            changed = true;
        }
        if (request.getImageSource() != null && !Objects.equals(recipe.getImageSource(), request.getImageSource())) {
            recipe.setImageSource(request.getImageSource());
            changed = true;
        }
        if (request.getImageStatus() != null && !Objects.equals(recipe.getImageStatus(), request.getImageStatus())) {
            recipe.setImageStatus(request.getImageStatus());
            recipe.setImageReviewedBy(adminEmail);
            recipe.setImageReviewedAt(java.time.LocalDateTime.now());
            changed = true;
        }
        if (request.getReviewNote() != null && !request.getReviewNote().isBlank()
                && request.getImageStatus() != null
                && request.getImageStatus() != ImageStatus.APPROVED) {
            recipe.setImageReviewNote(request.getReviewNote().trim());
            changed = true;
        }
        if (recipe.getVisibility() == RecipeVisibility.PUBLIC_ADMIN
                || recipe.getVerificationStatus() == VerificationStatus.VERIFIED) {
            validatePublicRecipeApproval(recipe);
        }
        if (changed) {
            recipe = recipeRepository.save(recipe);
            Map<String, Object> after = auditState(recipe);
            if (request.getReviewNote() != null && !request.getReviewNote().isBlank()) {
                after.put("reviewNote", request.getReviewNote().trim());
            }
            adminAuditService.record(
                    adminEmail,
                    AdminAuditActionType.RECIPE_REVIEW_UPDATE,
                    AdminAuditTargetType.RECIPE,
                    String.valueOf(recipe.getId()),
                    before,
                    after,
                    null
            );
        }
        return toDto(recipe);
    }

    private Specification<RecipeEntity> buildSpecification(String query,
                                                           VerificationStatus verificationStatus,
                                                           RecipeVisibility visibility,
                                                           Boolean archived,
                                                           String ownerEmail,
                                                           String mealType,
                                                           MarketRegion marketRegion,
                                                           ImageStatus imageStatus,
                                                           ImageSource imageSource) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            if (criteriaQuery != null && !Long.class.equals(criteriaQuery.getResultType())) {
                root.fetch("ownerUser", JoinType.LEFT);
                criteriaQuery.distinct(true);
            }
            List<Predicate> predicates = new ArrayList<>();
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), like));
            }
            if (verificationStatus != null) {
                predicates.add(criteriaBuilder.equal(root.get("verificationStatus"), verificationStatus));
            }
            if (visibility != null) {
                predicates.add(criteriaBuilder.equal(root.get("visibility"), visibility));
            }
            if (archived != null) {
                predicates.add(criteriaBuilder.equal(root.get("archived"), archived));
            }
            if (ownerEmail != null && !ownerEmail.isBlank()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.join("ownerUser", JoinType.LEFT).get("email")),
                        "%" + ownerEmail.trim().toLowerCase(Locale.ROOT) + "%"
                ));
            }
            if (mealType != null && !mealType.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("mealType"), mealType.trim().toUpperCase(Locale.ROOT)));
            }
            if (marketRegion != null) {
                predicates.add(criteriaBuilder.equal(root.get("marketRegion"), marketRegion));
            }
            if (imageStatus != null) {
                predicates.add(criteriaBuilder.equal(root.get("imageStatus"), imageStatus));
            }
            if (imageSource != null) {
                predicates.add(criteriaBuilder.equal(root.get("imageSource"), imageSource));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private RecipeEntity findRecipe(Long id) {
        return recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
    }

    private AdminRecipeDto toDto(RecipeEntity recipe) {
        AdminRecipeDto dto = new AdminRecipeDto();
        dto.setId(recipe.getId());
        if (recipe.getOwnerUser() != null) {
            dto.setOwnerUserId(recipe.getOwnerUser().getId());
            dto.setOwnerEmail(recipe.getOwnerUser().getEmail());
        }
        dto.setName(recipe.getName());
        dto.setDescription(recipe.getDescription());
        dto.setMealType(recipe.getMealType());
        dto.setVisibility(recipe.getVisibility());
        dto.setVerificationStatus(recipe.getVerificationStatus());
        dto.setMarketRegion(recipe.getMarketRegion());
        dto.setLanguage(recipe.getLanguage());
        dto.setImageUrl(recipe.getImageUrl());
        dto.setImageSource(recipe.getImageSource());
        dto.setImageStatus(recipe.getImageStatus());
        dto.setImageReviewNote(recipe.getImageReviewNote());
        dto.setImageReviewedBy(recipe.getImageReviewedBy());
        dto.setImageReviewedAt(recipe.getImageReviewedAt());
        dto.setTotalYieldGrams(recipe.getTotalYieldGrams());
        dto.setDefaultServingGrams(recipe.getDefaultServingGrams());
        dto.setServingCount(recipe.getServingCount());
        dto.setCalories(round(recipe.getSnapshotCalories()));
        dto.setProtein(round(recipe.getSnapshotProtein()));
        dto.setCarbs(round(recipe.getSnapshotCarbs()));
        dto.setFat(round(recipe.getSnapshotFat()));
        dto.setFiber(round(recipe.getSnapshotFiber()));
        dto.setSugar(round(recipe.getSnapshotSugar()));
        dto.setSodium(round(recipe.getSnapshotSodium()));
        dto.setSavedCount(recipeUserInteractionRepository.countByRecipeAndSavedTrue(recipe));
        dto.setFavoriteCount(recipeUserInteractionRepository.countByRecipeAndFavoriteTrue(recipe));
        dto.setRatingCount(recipeUserInteractionRepository.countByRecipeAndRatingIsNotNull(recipe));
        dto.setAverageRating(round(recipeUserInteractionRepository.averageRating(recipe)));
        dto.setCategories(recipe.getCategories());
        dto.setArchived(Boolean.TRUE.equals(recipe.getArchived()));
        dto.setIngredientCount(recipe.getIngredients() == null ? 0 : recipe.getIngredients().size());
        dto.setCreatedAt(recipe.getCreatedAt());
        dto.setUpdatedAt(recipe.getUpdatedAt());
        dto.setIngredients(recipe.getIngredients() == null
                ? List.of()
                : recipe.getIngredients().stream().map(this::toIngredientDto).toList());
        return dto;
    }

    private RecipeIngredientDto toIngredientDto(RecipeIngredientEntity ingredient) {
        RecipeIngredientDto dto = new RecipeIngredientDto();
        dto.setFoodItemId(ingredient.getFoodItem().getId());
        dto.setFoodName(ingredient.getFoodItem().getName());
        dto.setPortionSize(ingredient.getPortionSize());
        dto.setPortionUnit(FoodPortionCalculator.resolveUnit(ingredient.getPortionUnit()));
        dto.setNormalizedPortionGrams(ingredient.getNormalizedPortionGrams());
        return dto;
    }

    private Map<String, Object> auditState(RecipeEntity recipe) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", recipe.getId());
        values.put("visibility", recipe.getVisibility());
        values.put("verificationStatus", recipe.getVerificationStatus());
        values.put("archived", recipe.getArchived());
        values.put("imageUrl", recipe.getImageUrl());
        values.put("imageSource", recipe.getImageSource());
        values.put("imageStatus", recipe.getImageStatus());
        return values;
    }

    private void validatePublicRecipeApproval(RecipeEntity recipe) {
        if (recipe.getVisibility() != RecipeVisibility.PUBLIC_ADMIN
                || recipe.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new IllegalArgumentException("Public recipes must be approved as PUBLIC_ADMIN and VERIFIED together.");
        }
        if (Boolean.TRUE.equals(recipe.getArchived())) {
            throw new IllegalArgumentException("Archived recipes cannot be approved for public discovery.");
        }
        if (recipe.getCategories() == null || recipe.getCategories().isEmpty()) {
            throw new IllegalArgumentException("At least one recipe category is required before public approval.");
        }
        if (recipe.getTotalYieldGrams() == null || recipe.getTotalYieldGrams() <= 0
                || recipe.getDefaultServingGrams() == null || recipe.getDefaultServingGrams() <= 0) {
            throw new IllegalArgumentException("Recipe yield and default serving must be valid before public approval.");
        }
        if (recipe.getSnapshotCalories() == null || recipe.getSnapshotCalories() <= 0) {
            throw new IllegalArgumentException("Recipe nutrition must be calculated before public approval.");
        }
        if (recipe.getImageUrl() == null || recipe.getImageUrl().isBlank()
                || recipe.getImageStatus() != ImageStatus.APPROVED) {
            throw new IllegalArgumentException("Public recipes require an approved image.");
        }
    }

    private Double round(Double value) {
        return value == null ? 0.0 : Math.round(value * 100.0) / 100.0;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
