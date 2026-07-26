package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminRecipeCreateRequestDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.AdminRecipeDto;
import com.grun.calorietracker.dto.AdminRecipeImportBatchRequestDto;
import com.grun.calorietracker.dto.AdminRecipeImportCandidateDto;
import com.grun.calorietracker.dto.AdminRecipeImportCandidatePageDto;
import com.grun.calorietracker.dto.AdminRecipeImportCandidateRequestDto;
import com.grun.calorietracker.dto.AdminRecipeImportIngredientUpdateRequestDto;
import com.grun.calorietracker.dto.AdminRecipeImportResultDto;
import com.grun.calorietracker.dto.AdminRecipeImportReviewRequestDto;
import com.grun.calorietracker.dto.AdminRecipePageDto;
import com.grun.calorietracker.dto.AdminRecipeReviewRequestDto;
import com.grun.calorietracker.dto.RecipeDto;
import com.grun.calorietracker.dto.RecipeIngredientRequestDto;
import com.grun.calorietracker.dto.RecipeRequestDto;
import com.grun.calorietracker.dto.RecipeStepRequestDto;
import com.grun.calorietracker.dto.RecipeStepDto;
import com.grun.calorietracker.dto.RecipeIngredientDto;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.RecipeImportCandidateEntity;
import com.grun.calorietracker.entity.RecipeCookingStepEntity;
import com.grun.calorietracker.entity.RecipeIngredientEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.RecipeAllergen;
import com.grun.calorietracker.enums.RecipeImportCandidateStatus;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.RecipeImportCandidateRepository;
import com.grun.calorietracker.repository.RecipeRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.RecipeUserInteractionRepository;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.AdminRecipeService;
import com.grun.calorietracker.service.RecipeService;
import com.grun.calorietracker.service.PushDeliveryService;
import com.grun.calorietracker.service.support.FoodPortionCalculator;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminRecipeServiceImpl implements AdminRecipeService {

    private final RecipeRepository recipeRepository;
    private final RecipeImportCandidateRepository recipeImportCandidateRepository;
    private final RecipeUserInteractionRepository recipeUserInteractionRepository;
    private final FoodItemRepository foodItemRepository;
    private final RecipeService recipeService;
    private final AdminAuditService adminAuditService;
    private final NotificationRepository notificationRepository;
    private final PushDeliveryService pushDeliveryService;
    private final ObjectMapper objectMapper;

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
                                          RecipeAllergen allergen,
                                          int page,
                                          int size) {
        Page<RecipeEntity> recipes = recipeRepository.findAll(
                buildSpecification(query, verificationStatus, visibility, archived, ownerEmail, mealType, marketRegion, imageStatus, imageSource, allergen),
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
    public AdminRecipeDto createRecipe(AdminRecipeCreateRequestDto request, String adminEmail) {
        if (request == null || request.getRecipe() == null) {
            throw new IllegalArgumentException("Admin recipe create request must not be empty.");
        }
        String ownerEmail = trimToNull(request.getOwnerEmail());
        if (ownerEmail == null) {
            ownerEmail = trimToNull(adminEmail);
        }
        if (ownerEmail == null) {
            throw new IllegalArgumentException("Recipe owner email is required.");
        }

        RecipeDto created = recipeService.createRecipe(ownerEmail, request.getRecipe());
        RecipeEntity recipe = findRecipe(created.getId());
        adminAuditService.record(
                adminEmail,
                AdminAuditActionType.RECIPE_CREATE,
                AdminAuditTargetType.RECIPE,
                String.valueOf(recipe.getId()),
                null,
                auditState(recipe),
                trimToNull(request.getReviewNote())
        );

        if (hasInitialReviewState(request)) {
            AdminRecipeReviewRequestDto review = new AdminRecipeReviewRequestDto();
            RecipeVisibility requestedVisibility = request.getVisibility();
            VerificationStatus requestedStatus = request.getVerificationStatus();
            ImageStatus requestedImageStatus = request.getImageStatus();
            ImageSource requestedImageSource = request.getImageSource();
            if (requestedVisibility == RecipeVisibility.PUBLIC_ADMIN) {
                requestedStatus = requestedStatus == null ? VerificationStatus.VERIFIED : requestedStatus;
                requestedImageStatus = requestedImageStatus == null ? ImageStatus.APPROVED : requestedImageStatus;
                requestedImageSource = requestedImageSource == null ? ImageSource.ADMIN_UPLOAD : requestedImageSource;
            }
            review.setVisibility(requestedVisibility);
            review.setVerificationStatus(requestedStatus);
            review.setArchived(Boolean.TRUE.equals(request.getArchived()));
            review.setImageStatus(requestedImageStatus);
            review.setImageSource(requestedImageSource);
            review.setImageUrl(request.getRecipe().getImageUrl());
            review.setCategories(request.getRecipe().getCategories());
            review.setReviewNote(trimToNull(request.getReviewNote()) == null ? "Created from admin panel." : request.getReviewNote().trim());
            return updateRecipeReview(recipe.getId(), review, adminEmail);
        }
        return toDto(recipe);
    }
    @Override
    @Transactional
    public void archiveRecipe(Long id, String adminEmail) {
        RecipeEntity recipe = findRecipe(id);
        Map<String, Object> before = auditState(recipe);
        recipe.setArchived(true);
        recipeRepository.save(recipe);
        Map<String, Object> after = auditState(recipe);
        adminAuditService.record(
                adminEmail,
                AdminAuditActionType.RECIPE_REVIEW_UPDATE,
                AdminAuditTargetType.RECIPE,
                String.valueOf(recipe.getId()),
                before,
                after,
                "Recipe archived from admin panel. Existing diary logs keep their immutable nutrition snapshots."
        );
    }
    @Override
    @Transactional
    public AdminRecipeDto updateRecipeReview(Long id, AdminRecipeReviewRequestDto request, String adminEmail) {
        if (request == null) {
            throw new IllegalArgumentException("Recipe review request must not be empty.");
        }
        RecipeEntity recipe = findRecipe(id);
        boolean userPublicationPending = recipe.getVisibility() == RecipeVisibility.COMMUNITY_PENDING;
        String reviewNote = trimToNull(request.getReviewNote());
        if (userPublicationPending
                && request.getVerificationStatus() == VerificationStatus.REJECTED
                && reviewNote == null) {
            throw new IllegalArgumentException("A rejection reason is required for a user-submitted recipe.");
        }

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
        if (request.getCategories() != null && !Objects.equals(recipe.getCategories(), request.getCategories())) {
            recipe.setCategories(new LinkedHashSet<>(request.getCategories()));
            changed = true;
        }
        if (request.getAllergens() != null && !Objects.equals(recipe.getAllergens(), request.getAllergens())) {
            recipe.setAllergens(new LinkedHashSet<>(request.getAllergens()));
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
            recipe.setImageReviewedAt(LocalDateTime.now());
            changed = true;
        }
        if (reviewNote != null
                && request.getImageStatus() != null
                && request.getImageStatus() != ImageStatus.APPROVED) {
            recipe.setImageReviewNote(reviewNote);
            changed = true;
        }
        if (request.getCookingSteps() != null) {
            replaceCookingSteps(recipe, request.getCookingSteps());
            changed = true;
        }
        if (recipe.getVerificationStatus() == VerificationStatus.REJECTED
                && recipe.getVisibility() != RecipeVisibility.PRIVATE) {
            recipe.setVisibility(RecipeVisibility.PRIVATE);
            changed = true;
        }
        if (recipe.getVisibility() == RecipeVisibility.PUBLIC_ADMIN
                || recipe.getVerificationStatus() == VerificationStatus.VERIFIED) {
            validatePublicRecipeApproval(recipe);
        }
        if (changed) {
            recipe = recipeRepository.save(recipe);
            Map<String, Object> after = auditState(recipe);
            if (reviewNote != null) {
                after.put("reviewNote", reviewNote);
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
            notifyRecipeOwnerAboutReviewDecision(recipe, userPublicationPending, reviewNote);
        }
        return toDto(recipe);
    }
    @Override
    @Transactional
    public AdminRecipeImportResultDto importRecipeCandidates(AdminRecipeImportBatchRequestDto request, String adminEmail) {
        if (request == null || request.getRecipes() == null || request.getRecipes().isEmpty()) {
            throw new IllegalArgumentException("Recipe import JSON must contain at least one recipe candidate.");
        }
        String batchId = trimToNull(request.getBatchId());
        if (batchId == null) {
            batchId = "recipe-import-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        }
        List<AdminRecipeImportCandidateDto> candidates = new ArrayList<>();
        int skipped = 0;
        int failed = 0;
        for (AdminRecipeImportCandidateRequestDto item : request.getRecipes()) {
            try {
                if (item == null || trimToNull(item.getSourceKey()) == null || item.getRecipe() == null || trimToNull(item.getRecipe().getName()) == null) {
                    failed++;
                    continue;
                }
                if (recipeImportCandidateRepository.existsByBatchIdAndSourceKey(batchId, item.getSourceKey().trim())) {
                    skipped++;
                    continue;
                }
                RecipeImportCandidateEntity candidate = new RecipeImportCandidateEntity();
                candidate.setBatchId(batchId);
                candidate.setSourceKey(item.getSourceKey().trim());
                candidate.setSourceTitle(trimToNull(item.getSourceTitle()));
                candidate.setSourceUrl(trimToNull(item.getSourceUrl()));
                candidate.setSourceRevisionUrl(trimToNull(item.getSourceRevisionUrl()));
                candidate.setLicense(trimToNull(item.getLicense() == null && request.getSourcePolicy() != null ? request.getSourcePolicy().getLicense() : item.getLicense()));
                candidate.setRecommendedImportStatus(trimToNull(item.getRecommendedImportStatus()));
                candidate.setStatus(RecipeImportCandidateStatus.PENDING);
                candidate.setRecipeName(item.getRecipe().getName().trim());
                candidate.setMealType(normalizeMealType(item.getRecipe().getMealType()));
                candidate.setMarketRegion(item.getRecipe().getMarketRegion());
                candidate.setLanguage(trimToNull(item.getRecipe().getLanguage()));
                List<AdminRecipeImportCandidateRequestDto.IngredientPayload> ingredients = item.getRecipe().getIngredients() == null ? List.of() : item.getRecipe().getIngredients();
                candidate.setIngredientCount(ingredients.size());
                candidate.setUnresolvedIngredientCount((int) ingredients.stream().filter(ingredient -> ingredient.getFoodItemId() == null).count());
                candidate.setValidationIssues(buildImportValidationIssues(item));
                candidate.setRawPayload(writeRawPayload(item));
                candidates.add(toImportDto(recipeImportCandidateRepository.save(candidate)));
            } catch (RuntimeException ex) {
                failed++;
            }
        }
        AdminRecipeImportResultDto dto = new AdminRecipeImportResultDto();
        dto.setBatchId(batchId);
        dto.setTotalCandidates(request.getRecipes().size());
        dto.setCreatedCandidates(candidates.size());
        dto.setSkippedDuplicates(skipped);
        dto.setFailedCandidates(failed);
        dto.setCandidates(candidates);
        adminAuditService.record(
                adminEmail,
                AdminAuditActionType.RECIPE_CREATE,
                AdminAuditTargetType.RECIPE,
                "import-batch:" + batchId,
                null,
                Map.of("createdCandidates", candidates.size(), "skippedDuplicates", skipped, "failedCandidates", failed),
                "Recipe JSON import candidates created. Candidates are not public until admin review."
        );
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminRecipeImportCandidatePageDto listImportCandidates(RecipeImportCandidateStatus status, String batchId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedBatchId = trimToNull(batchId);
        Page<RecipeImportCandidateEntity> candidates;
        if (status != null && normalizedBatchId != null) {
            candidates = recipeImportCandidateRepository.findByStatusAndBatchIdContainingIgnoreCase(status, normalizedBatchId, pageable);
        } else if (status != null) {
            candidates = recipeImportCandidateRepository.findByStatus(status, pageable);
        } else if (normalizedBatchId != null) {
            candidates = recipeImportCandidateRepository.findByBatchIdContainingIgnoreCase(normalizedBatchId, pageable);
        } else {
            candidates = recipeImportCandidateRepository.findAll(pageable);
        }
        AdminRecipeImportCandidatePageDto dto = new AdminRecipeImportCandidatePageDto();
        dto.setContent(candidates.getContent().stream().map(this::toImportDto).toList());
        dto.setPage(candidates.getNumber());
        dto.setSize(candidates.getSize());
        dto.setTotalElements(candidates.getTotalElements());
        dto.setTotalPages(candidates.getTotalPages());
        dto.setFirst(candidates.isFirst());
        dto.setLast(candidates.isLast());
        return dto;
    }

    @Override
    @Transactional
    public AdminRecipeDto approveImportCandidate(Long id, AdminRecipeImportReviewRequestDto request, String adminEmail) {
        RecipeImportCandidateEntity candidate = findImportCandidate(id);
        if (candidate.getStatus() != RecipeImportCandidateStatus.PENDING) {
            throw new IllegalArgumentException("Only pending recipe import candidates can be approved.");
        }
        if (candidate.getUnresolvedIngredientCount() != null && candidate.getUnresolvedIngredientCount() > 0) {
            throw new IllegalArgumentException("Recipe import candidate has unresolved ingredients. Add foodItemId values before approval.");
        }
        AdminRecipeImportCandidateRequestDto source = readRawPayload(candidate.getRawPayload());
        AdminRecipeCreateRequestDto createRequest = toCreateRequest(source, request);
        AdminRecipeDto created = createRecipe(createRequest, adminEmail);
        candidate.setStatus(RecipeImportCandidateStatus.APPROVED);
        candidate.setCreatedRecipeId(created.getId());
        candidate.setReviewedBy(adminEmail);
        candidate.setReviewedAt(java.time.LocalDateTime.now());
        candidate.setReviewNote(reviewNote(request, "Approved import candidate and moved to recipe review queue."));
        recipeImportCandidateRepository.save(candidate);
        adminAuditService.record(
                adminEmail,
                AdminAuditActionType.RECIPE_REVIEW_UPDATE,
                AdminAuditTargetType.RECIPE,
                "import-candidate:" + candidate.getId(),
                null,
                Map.of("createdRecipeId", created.getId(), "status", candidate.getStatus()),
                candidate.getReviewNote()
        );
        return created;
    }

    @Override
    @Transactional
    public AdminRecipeImportCandidateDto rejectImportCandidate(Long id, AdminRecipeImportReviewRequestDto request, String adminEmail) {
        RecipeImportCandidateEntity candidate = findImportCandidate(id);
        if (candidate.getStatus() != RecipeImportCandidateStatus.PENDING) {
            throw new IllegalArgumentException("Only pending recipe import candidates can be rejected.");
        }
        candidate.setStatus(RecipeImportCandidateStatus.REJECTED);
        candidate.setReviewedBy(adminEmail);
        candidate.setReviewedAt(java.time.LocalDateTime.now());
        candidate.setReviewNote(reviewNote(request, "Rejected import candidate."));
        recipeImportCandidateRepository.save(candidate);
        adminAuditService.record(
                adminEmail,
                AdminAuditActionType.RECIPE_REVIEW_UPDATE,
                AdminAuditTargetType.RECIPE,
                "import-candidate:" + candidate.getId(),
                null,
                Map.of("status", candidate.getStatus(), "sourceKey", candidate.getSourceKey()),
                candidate.getReviewNote()
        );
        return toImportDto(candidate);
    }
    private void notifyRecipeOwnerAboutReviewDecision(
            RecipeEntity recipe,
            boolean userPublicationPending,
            String reviewNote) {
        if (!userPublicationPending || recipe.getOwnerUser() == null) {
            return;
        }

        String type;
        String severity;
        if (recipe.getVisibility() == RecipeVisibility.PUBLIC_ADMIN
                && recipe.getVerificationStatus() == VerificationStatus.VERIFIED) {
            type = "recipe_review_approved";
            severity = "INFO";
        } else if (recipe.getVerificationStatus() == VerificationStatus.REJECTED) {
            type = "recipe_review_rejected";
            severity = "WARNING";
        } else {
            return;
        }

        NotificationEntity notification = new NotificationEntity();
        notification.setUser(recipe.getOwnerUser());
        notification.setType(type);
        boolean approved = "recipe_review_approved".equals(type);
        boolean turkish = recipe.getOwnerUser().getPreferredLanguage() == PreferredLanguage.TR;
        notification.setTitle(approved
                ? (turkish ? "Tarifin yayında!" : "Your recipe is live!")
                : (turkish ? "Birkaç düzenleme gerekiyor" : "A few tweaks needed"));
        String message = approved
                ? (turkish
                    ? "\"%s\" tarifin onaylandı ve yayınlandı.".formatted(recipe.getName())
                    : "Your recipe \"%s\" was approved and published.".formatted(recipe.getName()))
                : (turkish
                    ? "\"%s\" tarifin onaylanmadı.".formatted(recipe.getName())
                    : "Your recipe \"%s\" was not approved.".formatted(recipe.getName()));
        notification.setNote(approved ? null : reviewNote);
        notification.setPrimaryAction(approved ? "VIEW_RECIPE" : "EDIT_RECIPE");
        notification.setSeverity(severity);
        notification.setSource("RECIPE_REVIEW");
        notification.setTargetType("RECIPE");
        notification.setTargetId(String.valueOf(recipe.getId()));
        notification.setTargetRoute("recipes");
        notification.setMessage(message);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        NotificationEntity saved = notificationRepository.save(notification);
        try {
            pushDeliveryService.deliver(saved);
        } catch (RuntimeException ex) {
            log.warn("recipe_review_push_delivery_failed recipeId={} notificationId={} reason={}",
                    recipe.getId(), saved.getId(), ex.getMessage());
        }
    }
    private boolean hasInitialReviewState(AdminRecipeCreateRequestDto request) {
        return request.getVerificationStatus() != null
                || request.getVisibility() != null
                || request.getArchived() != null
                || request.getImageStatus() != null
                || request.getImageSource() != null;
    }
    @Override
    @Transactional
    public AdminRecipeImportCandidateDto updateImportCandidateIngredient(Long id, int ingredientIndex, AdminRecipeImportIngredientUpdateRequestDto request, String adminEmail) {
        if (request == null || request.getFoodItemId() == null) {
            throw new IllegalArgumentException("foodItemId is required.");
        }
        if (!foodItemRepository.existsById(request.getFoodItemId())) {
            throw new ResourceNotFoundException("Food item not found");
        }
        RecipeImportCandidateEntity candidate = findImportCandidate(id);
        if (candidate.getStatus() != RecipeImportCandidateStatus.PENDING) {
            throw new IllegalArgumentException("Only pending recipe import candidates can be edited.");
        }
        AdminRecipeImportCandidateRequestDto source = readRawPayload(candidate.getRawPayload());
        List<AdminRecipeImportCandidateRequestDto.IngredientPayload> ingredients = source.getRecipe() == null ? List.of() : source.getRecipe().getIngredients();
        if (ingredientIndex < 0 || ingredientIndex >= ingredients.size()) {
            throw new IllegalArgumentException("Ingredient index is invalid.");
        }
        AdminRecipeImportCandidateRequestDto.IngredientPayload ingredient = ingredients.get(ingredientIndex);
        Long previousFoodItemId = ingredient.getFoodItemId();
        ingredient.setFoodItemId(request.getFoodItemId());
        candidate.setRawPayload(writeRawPayload(source));
        candidate.setIngredientCount(ingredients.size());
        candidate.setUnresolvedIngredientCount((int) ingredients.stream().filter(item -> item.getFoodItemId() == null).count());
        candidate.setValidationIssues(buildImportValidationIssues(source));
        RecipeImportCandidateEntity saved = recipeImportCandidateRepository.save(candidate);
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("ingredientIndex", ingredientIndex);
        before.put("foodItemId", previousFoodItemId);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("ingredientIndex", ingredientIndex);
        after.put("foodItemId", request.getFoodItemId());
        after.put("unresolvedIngredientCount", saved.getUnresolvedIngredientCount());
        adminAuditService.record(
                adminEmail,
                AdminAuditActionType.RECIPE_REVIEW_UPDATE,
                AdminAuditTargetType.RECIPE,
                "import-candidate:" + candidate.getId(),
                before,
                after,
                "Recipe import ingredient mapped from admin UI."
        );
        return toImportDto(saved);
    }
    private RecipeImportCandidateEntity findImportCandidate(Long id) {
        return recipeImportCandidateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe import candidate not found"));
    }

    private AdminRecipeImportCandidateDto toImportDto(RecipeImportCandidateEntity candidate) {
        AdminRecipeImportCandidateDto dto = new AdminRecipeImportCandidateDto();
        dto.setId(candidate.getId());
        dto.setBatchId(candidate.getBatchId());
        dto.setSourceKey(candidate.getSourceKey());
        dto.setSourceTitle(candidate.getSourceTitle());
        dto.setSourceUrl(candidate.getSourceUrl());
        dto.setSourceRevisionUrl(candidate.getSourceRevisionUrl());
        dto.setLicense(candidate.getLicense());
        dto.setRecommendedImportStatus(candidate.getRecommendedImportStatus());
        dto.setStatus(candidate.getStatus());
        dto.setRecipeName(candidate.getRecipeName());
        dto.setMealType(candidate.getMealType());
        dto.setMarketRegion(candidate.getMarketRegion());
        dto.setLanguage(candidate.getLanguage());
        try {
            AdminRecipeImportCandidateRequestDto source = readRawPayload(candidate.getRawPayload());
            if (source.getRecipe() != null) {
                dto.setImageUrl(source.getRecipe().getImageUrl());
            }
        } catch (RuntimeException ignored) {
            // Raw import payload is best-effort metadata for admin UI display.
        }
        dto.setIngredientCount(candidate.getIngredientCount());
        dto.setUnresolvedIngredientCount(candidate.getUnresolvedIngredientCount());
        dto.setValidationIssues(candidate.getValidationIssues());
        dto.setCreatedRecipeId(candidate.getCreatedRecipeId());
        dto.setReviewedBy(candidate.getReviewedBy());
        dto.setReviewedAt(candidate.getReviewedAt());
        dto.setReviewNote(candidate.getReviewNote());
        dto.setCreatedAt(candidate.getCreatedAt());
        dto.setUpdatedAt(candidate.getUpdatedAt());
        dto.setIngredients(toImportIngredients(candidate));
        dto.setCookingSteps(toImportCookingSteps(candidate));
        return dto;
    }

    private List<AdminRecipeImportCandidateDto.IngredientDto> toImportIngredients(RecipeImportCandidateEntity candidate) {
        try {
            AdminRecipeImportCandidateRequestDto source = readRawPayload(candidate.getRawPayload());
            if (source.getRecipe() == null || source.getRecipe().getIngredients() == null) {
                return List.of();
            }
            List<AdminRecipeImportCandidateDto.IngredientDto> result = new ArrayList<>();
            for (int index = 0; index < source.getRecipe().getIngredients().size(); index++) {
                AdminRecipeImportCandidateRequestDto.IngredientPayload sourceIngredient = source.getRecipe().getIngredients().get(index);
                AdminRecipeImportCandidateDto.IngredientDto dto = new AdminRecipeImportCandidateDto.IngredientDto();
                dto.setIndex(index);
                dto.setFoodItemId(sourceIngredient.getFoodItemId());
                dto.setIngredientName(sourceIngredient.getIngredientName());
                dto.setImageUrl(sourceIngredient.getImageUrl());
                dto.setPortionSize(sourceIngredient.getPortionSize());
                dto.setPortionUnit(sourceIngredient.getPortionUnit());
                dto.setEstimatedGrams(sourceIngredient.getEstimatedGrams());
                result.add(dto);
            }
            return result;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private List<AdminRecipeImportCandidateDto.CookingStepDto> toImportCookingSteps(RecipeImportCandidateEntity candidate) {
        try {
            AdminRecipeImportCandidateRequestDto source = readRawPayload(candidate.getRawPayload());
            if (source.getRecipe() == null || source.getRecipe().getCookingSteps() == null) {
                return List.of();
            }
            List<AdminRecipeImportCandidateDto.CookingStepDto> result = new ArrayList<>();
            for (int index = 0; index < source.getRecipe().getCookingSteps().size(); index++) {
                AdminRecipeImportCandidateRequestDto.CookingStepPayload sourceStep = source.getRecipe().getCookingSteps().get(index);
                if (sourceStep == null || sourceStep.getInstruction() == null || sourceStep.getInstruction().isBlank()) {
                    continue;
                }
                AdminRecipeImportCandidateDto.CookingStepDto dto = new AdminRecipeImportCandidateDto.CookingStepDto();
                dto.setStepNumber(index + 1);
                dto.setInstruction(sourceStep.getInstruction().trim());
                result.add(dto);
            }
            return result;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }
    private String writeRawPayload(AdminRecipeImportCandidateRequestDto candidate) {
        try {
            return objectMapper.writeValueAsString(candidate);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Recipe import candidate could not be serialized.");
        }
    }

    private AdminRecipeImportCandidateRequestDto readRawPayload(String rawPayload) {
        try {
            return objectMapper.readValue(rawPayload, AdminRecipeImportCandidateRequestDto.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Recipe import candidate raw payload could not be read.");
        }
    }

    private AdminRecipeCreateRequestDto toCreateRequest(AdminRecipeImportCandidateRequestDto source, AdminRecipeImportReviewRequestDto reviewRequest) {
        AdminRecipeImportCandidateRequestDto.RecipePayload sourceRecipe = source.getRecipe();
        RecipeRequestDto recipe = new RecipeRequestDto();
        recipe.setName(sourceRecipe.getName());
        recipe.setDescription(sourceRecipe.getDescription());
        recipe.setMealType(normalizeMealType(sourceRecipe.getMealType()));
        recipe.setMarketRegion(sourceRecipe.getMarketRegion());
        recipe.setLanguage(sourceRecipe.getLanguage());
        recipe.setImageUrl(sourceRecipe.getImageUrl());
        recipe.setTotalYieldGrams(sourceRecipe.getTotalYieldGrams());
        recipe.setDefaultServingGrams(sourceRecipe.getDefaultServingGrams());
        recipe.setServingCount(sourceRecipe.getServingCount());
        recipe.setCategories(sourceRecipe.getCategories() == null ? Collections.emptySet() : sourceRecipe.getCategories());
        recipe.setAllergens(sourceRecipe.getAllergens() == null ? Collections.emptySet() : sourceRecipe.getAllergens());
        recipe.setIngredients(toRecipeIngredients(sourceRecipe.getIngredients()));
        recipe.setCookingSteps(toRecipeSteps(sourceRecipe.getCookingSteps()));

        AdminRecipeCreateRequestDto request = new AdminRecipeCreateRequestDto();
        request.setRecipe(recipe);
        request.setVisibility(RecipeVisibility.COMMUNITY_PENDING);
        request.setVerificationStatus(VerificationStatus.NEEDS_REVIEW);
        request.setImageStatus(recipe.getImageUrl() == null || recipe.getImageUrl().isBlank() ? null : ImageStatus.NEEDS_REVIEW);
        request.setImageSource(recipe.getImageUrl() == null || recipe.getImageUrl().isBlank() ? null : ImageSource.ADMIN_UPLOAD);
        request.setReviewNote(reviewNote(reviewRequest, "Approved open-source recipe import candidate. Final public approval still required."));
        return request;
    }

    private List<RecipeIngredientRequestDto> toRecipeIngredients(List<AdminRecipeImportCandidateRequestDto.IngredientPayload> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            throw new IllegalArgumentException("Imported recipe must contain ingredients before approval.");
        }
        List<RecipeIngredientRequestDto> mapped = new ArrayList<>();
        for (AdminRecipeImportCandidateRequestDto.IngredientPayload ingredient : ingredients) {
            if (ingredient.getFoodItemId() == null) {
                throw new IllegalArgumentException("Imported recipe ingredient is missing foodItemId: " + ingredient.getIngredientName());
            }
            RecipeIngredientRequestDto dto = new RecipeIngredientRequestDto();
            dto.setFoodItemId(ingredient.getFoodItemId());
            dto.setPortionSize(ingredient.getPortionSize() == null ? ingredient.getEstimatedGrams() : ingredient.getPortionSize());
            dto.setPortionUnit(ingredient.getPortionUnit() == null ? com.grun.calorietracker.enums.FoodPortionUnit.GRAM : ingredient.getPortionUnit());
            mapped.add(dto);
        }
        return mapped;
    }

    private List<RecipeStepRequestDto> toRecipeSteps(List<AdminRecipeImportCandidateRequestDto.CookingStepPayload> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        return steps.stream()
                .filter(step -> step != null && step.getInstruction() != null && !step.getInstruction().isBlank())
                .map(step -> {
                    RecipeStepRequestDto dto = new RecipeStepRequestDto();
                    dto.setInstruction(step.getInstruction().trim());
                    return dto;
                })
                .toList();
    }

    private String buildImportValidationIssues(AdminRecipeImportCandidateRequestDto candidate) {
        List<String> issues = new ArrayList<>();
        if (candidate.getRecipe() == null) {
            issues.add("recipe payload missing");
            return String.join("; ", issues);
        }
        List<AdminRecipeImportCandidateRequestDto.IngredientPayload> ingredients = candidate.getRecipe().getIngredients();
        if (ingredients == null || ingredients.isEmpty()) {
            issues.add("ingredients missing");
        } else {
            long unresolved = ingredients.stream().filter(ingredient -> ingredient.getFoodItemId() == null).count();
            if (unresolved > 0) {
                issues.add(unresolved + " unresolved ingredient foodItemId value(s)");
            }
        }
        if (candidate.getRecipe().getCategories() == null || candidate.getRecipe().getCategories().isEmpty()) {
            issues.add("categories missing");
        }
        if (candidate.getRecipe().getTotalYieldGrams() == null || candidate.getRecipe().getTotalYieldGrams() <= 0) {
            issues.add("totalYieldGrams missing or invalid");
        }
        if (candidate.getRecipe().getDefaultServingGrams() == null || candidate.getRecipe().getDefaultServingGrams() <= 0) {
            issues.add("defaultServingGrams missing or invalid");
        }
        return issues.isEmpty() ? null : String.join("; ", issues);
    }

    private String reviewNote(AdminRecipeImportReviewRequestDto request, String fallback) {
        return request != null && request.getReviewNote() != null && !request.getReviewNote().isBlank()
                ? request.getReviewNote().trim()
                : fallback;
    }

    private void replaceCookingSteps(RecipeEntity recipe, List<RecipeStepRequestDto> steps) {
        recipe.getCookingSteps().clear();
        if (steps == null) {
            return;
        }
        int order = 0;
        for (RecipeStepRequestDto step : steps) {
            if (step == null || step.getInstruction() == null || step.getInstruction().isBlank()) {
                continue;
            }
            RecipeCookingStepEntity entity = new RecipeCookingStepEntity();
            entity.setRecipe(recipe);
            entity.setStepOrder(order++);
            entity.setInstruction(step.getInstruction().trim());
            recipe.getCookingSteps().add(entity);
        }
    }
    private Specification<RecipeEntity> buildSpecification(String query,
                                                           VerificationStatus verificationStatus,
                                                           RecipeVisibility visibility,
                                                           Boolean archived,
                                                           String ownerEmail,
                                                           String mealType,
                                                           MarketRegion marketRegion,
                                                           ImageStatus imageStatus,
                                                           ImageSource imageSource,
                                                           RecipeAllergen allergen) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            if (criteriaQuery != null && RecipeEntity.class.equals(criteriaQuery.getResultType())) {
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
            if (allergen != null) {
                Join<RecipeEntity, RecipeAllergen> allergenJoin = root.joinSet("allergens", JoinType.INNER);
                predicates.add(criteriaBuilder.equal(allergenJoin, allergen));
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
        dto.setCategories(copyCategories(recipe));
        dto.setAllergens(copyAllergens(recipe));
        dto.setArchived(Boolean.TRUE.equals(recipe.getArchived()));
        dto.setIngredientCount(recipe.getIngredients() == null ? 0 : recipe.getIngredients().size());
        dto.setCreatedAt(recipe.getCreatedAt());
        dto.setUpdatedAt(recipe.getUpdatedAt());
        dto.setIngredients(recipe.getIngredients() == null
                ? List.of()
                : recipe.getIngredients().stream().map(this::toIngredientDto).toList());
        dto.setCookingSteps(recipe.getCookingSteps() == null
                ? List.of()
                : recipe.getCookingSteps().stream().map(this::toStepDto).toList());
        return dto;
    }

    private LinkedHashSet<com.grun.calorietracker.enums.RecipeCategory> copyCategories(RecipeEntity recipe) {
        return recipe.getCategories() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(recipe.getCategories());
    }

    private LinkedHashSet<com.grun.calorietracker.enums.RecipeAllergen> copyAllergens(RecipeEntity recipe) {
        return recipe.getAllergens() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(recipe.getAllergens());
    }

    private RecipeStepDto toStepDto(RecipeCookingStepEntity step) {
        RecipeStepDto dto = new RecipeStepDto();
        dto.setStepNumber(step.getStepOrder() == null ? null : step.getStepOrder() + 1);
        dto.setInstruction(step.getInstruction());
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
        values.put("categories", copyCategories(recipe));
        values.put("allergens", copyAllergens(recipe));
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

    private String normalizeMealType(String mealType) {
        return mealType == null || mealType.isBlank() ? null : mealType.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
