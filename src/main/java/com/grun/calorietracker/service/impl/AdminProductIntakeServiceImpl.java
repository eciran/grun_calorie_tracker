package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminProductIntakePageDto;
import com.grun.calorietracker.dto.AdminProductIntakeAssignmentDto;
import com.grun.calorietracker.dto.AdminProductIntakeActionDto;
import com.grun.calorietracker.dto.AdminProductIntakeManualRequestDto;
import com.grun.calorietracker.dto.AdminProductIntakeDetailDto;
import com.grun.calorietracker.dto.AdminProductIntakeSummaryDto;
import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.FoodProductReviewCaseAssetEntity;
import com.grun.calorietracker.entity.FoodProductSourceEvidenceEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AdminProductIntakeQueue;
import com.grun.calorietracker.enums.FoodProductReviewCaseStatus;
import com.grun.calorietracker.enums.FoodProductResolutionMode;
import com.grun.calorietracker.enums.FoodProductReviewCaseSource;
import com.grun.calorietracker.enums.FoodProductReviewRiskLevel;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.enums.ProductIntakeApplyField;
import com.grun.calorietracker.repository.FoodProductReviewCaseRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.FoodProductReviewCaseAssetRepository;
import com.grun.calorietracker.repository.FoodProductSourceEvidenceRepository;
import com.grun.calorietracker.service.AdminProductIntakeService;
import com.grun.calorietracker.service.FoodProductReviewCaseService;
import com.grun.calorietracker.service.FoodProductReviewCaseEvidenceService;
import com.grun.calorietracker.service.CatalogPublicationService;
import com.grun.calorietracker.service.CatalogMediaService;
import com.grun.calorietracker.service.support.ProductIntakeCatalogMutationOrchestrator;
import com.grun.calorietracker.service.support.ProductIntakeApplyGate;
import com.grun.calorietracker.service.support.FoodProductIntakeMetrics;
import com.grun.calorietracker.service.support.FoodProductEvidenceExpiryScheduler;
import com.grun.calorietracker.service.model.FoodProductReviewCaseCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.Set;
import java.util.EnumMap;

@Service
public class AdminProductIntakeServiceImpl implements AdminProductIntakeService {
    private static final List<FoodProductReviewCaseStatus> OPEN_STATUSES = List.of(
            FoodProductReviewCaseStatus.SUBMITTED,
            FoodProductReviewCaseStatus.IN_REVIEW,
            FoodProductReviewCaseStatus.NEEDS_SUBMITTER_ACTION
    );

    private final FoodProductReviewCaseRepository repository;
    private final UserRepository userRepository;
    private final FoodItemRepository foodItemRepository;
    private final NotificationRepository notificationRepository;
    private final FoodProductReviewCaseAssetRepository assetRepository;
    private final FoodProductReviewCaseService reviewCaseService;
    private final ObjectMapper objectMapper;
    private final long overdueHours;
    private FoodProductReviewCaseEvidenceService evidenceService;
    private CatalogPublicationService catalogPublicationService;
    private ProductIntakeCatalogMutationOrchestrator catalogMutationOrchestrator;
    private FoodProductSourceEvidenceRepository sourceEvidenceRepository;
    private FoodProductEvidenceExpiryScheduler evidenceExpiryScheduler;
    private CatalogMediaService catalogMediaService;
    private ProductIntakeApplyGate applyGate;
    private FoodProductIntakeMetrics intakeMetrics;

    public AdminProductIntakeServiceImpl(
            FoodProductReviewCaseRepository repository,
            UserRepository userRepository,
            FoodItemRepository foodItemRepository,
            NotificationRepository notificationRepository,
            FoodProductReviewCaseAssetRepository assetRepository,
            FoodProductReviewCaseService reviewCaseService,
            ObjectMapper objectMapper,
            @Value("${grun.product-intake.admin.overdue-hours:24}") long overdueHours
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.foodItemRepository = foodItemRepository;
        this.notificationRepository = notificationRepository;
        this.assetRepository = assetRepository;
        this.reviewCaseService = reviewCaseService;
        this.objectMapper = objectMapper;
        this.overdueHours = Math.max(1, overdueHours);
    }

    @Autowired
    public void setEvidenceService(FoodProductReviewCaseEvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }
    @Autowired
    public void setCatalogPublicationService(CatalogPublicationService catalogPublicationService) {
        this.catalogPublicationService = catalogPublicationService;
    }
    @Autowired
    public void setCatalogMutationOrchestrator(ProductIntakeCatalogMutationOrchestrator catalogMutationOrchestrator) {
        this.catalogMutationOrchestrator = catalogMutationOrchestrator;
    }

    @Autowired
    public void setEvidenceExpiryScheduler(FoodProductEvidenceExpiryScheduler evidenceExpiryScheduler) {
        this.evidenceExpiryScheduler = evidenceExpiryScheduler;
    }
    @Autowired(required = false)
    public void setCatalogMediaService(CatalogMediaService catalogMediaService) {
        this.catalogMediaService = catalogMediaService;
    }
    @Autowired
    public void setSourceEvidenceRepository(FoodProductSourceEvidenceRepository sourceEvidenceRepository) {
        this.sourceEvidenceRepository = sourceEvidenceRepository;
    }
    @Autowired
    public void setApplyGate(ProductIntakeApplyGate applyGate) {
        this.applyGate = applyGate;
    }
    @Autowired
    public void setIntakeMetrics(FoodProductIntakeMetrics intakeMetrics) {
        this.intakeMetrics = intakeMetrics;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminProductIntakePageDto list(
            String adminEmail,
            AdminProductIntakeQueue queue,
            FoodProductReviewCaseStatus status,
            MarketRegion marketRegion,
            int page,
            int size
    ) {
        AdminProductIntakeQueue selectedQueue = queue == null ? AdminProductIntakeQueue.ALL : queue;
        var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Specification<FoodProductReviewCaseEntity> specification = (root, query, cb) -> cb.conjunction();
        if (status != null) specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (marketRegion != null) specification = specification.and((root, query, cb) -> cb.equal(root.get("marketRegion"), marketRegion));
        specification = specification.and(queueSpecification(selectedQueue, adminEmail));
        return AdminProductIntakePageDto.from(repository.findAll(specification, pageable).map(this::toSummary));
    }

    @Override
    @Transactional
    public AdminProductIntakeAssignmentDto claim(Long caseId, String actorEmail) {
        UserEntity actor = requireActiveCatalogAdmin(actorEmail);
        FoodProductReviewCaseEntity reviewCase = lockedCase(caseId);
        if (reviewCase.getAssignedAdminEmail() != null
                && !reviewCase.getAssignedAdminEmail().equalsIgnoreCase(actor.getEmail())) {
            throw new IllegalStateException("Product intake is already assigned.");
        }
        if (reviewCase.getAssignedAdminEmail() == null) {
            reviewCase.setAssignedAdminEmail(actor.getEmail());
            reviewCase.setReviewClaimedAt(LocalDateTime.now());
        }
        return assignment(repository.save(reviewCase));
    }

    @Override
    @Transactional
    public AdminProductIntakeAssignmentDto release(Long caseId, String actorEmail) {
        UserEntity actor = requireActiveAdmin(actorEmail);
        FoodProductReviewCaseEntity reviewCase = lockedCase(caseId);
        boolean owner = actor.getRole() == UserRole.OWNER;
        if (!owner && (reviewCase.getAssignedAdminEmail() == null
                || !reviewCase.getAssignedAdminEmail().equalsIgnoreCase(actor.getEmail()))) {
            throw new AccessDeniedException("Only the current assignee or owner can release this intake.");
        }
        reviewCase.setAssignedAdminEmail(null);
        reviewCase.setReviewClaimedAt(null);
        return assignment(repository.save(reviewCase));
    }

    @Override
    @Transactional
    public AdminProductIntakeAssignmentDto reassign(Long caseId, String actorEmail, String targetAdminEmail) {
        UserEntity actor = requireActiveAdmin(actorEmail);
        if (actor.getRole() != UserRole.OWNER) throw new AccessDeniedException("Owner account required.");
        UserEntity target = requireActiveCatalogAdmin(targetAdminEmail);
        FoodProductReviewCaseEntity reviewCase = lockedCase(caseId);
        reviewCase.setAssignedAdminEmail(target.getEmail());
        reviewCase.setReviewClaimedAt(LocalDateTime.now());
        return assignment(repository.save(reviewCase));
    }

    @Override
    @Transactional
    public AdminProductIntakeActionDto requestBetterEvidence(Long caseId, String actorEmail, String note) {
        UserEntity actor = requireActiveAdmin(actorEmail);
        FoodProductReviewCaseEntity reviewCase = lockedCase(caseId);
        requireAssignedOrOwner(reviewCase, actor);
        if (reviewCase.getStatus() != FoodProductReviewCaseStatus.SUBMITTED
                && reviewCase.getStatus() != FoodProductReviewCaseStatus.IN_REVIEW) {
            throw new IllegalStateException("Product intake cannot request evidence in its current state.");
        }
        reviewCase.setStatus(FoodProductReviewCaseStatus.NEEDS_SUBMITTER_ACTION);
        reviewCase.setReviewNote(note.trim());
        FoodProductReviewCaseEntity saved = repository.save(reviewCase);
        if (saved.getSubmittedBy() != null) notificationRepository.save(evidenceNotification(saved, note));
        return action(saved);
    }

    @Override
    @Transactional
    public AdminProductIntakeActionDto decideEvidence(Long caseId, String actorEmail, boolean approved, String note) {
        UserEntity actor = requireActiveAdmin(actorEmail);
        FoodProductReviewCaseEntity reviewCase = lockedCase(caseId);
        requireAssignedOrOwner(reviewCase, actor);
        if (reviewCase.getStatus() != FoodProductReviewCaseStatus.SUBMITTED
                && reviewCase.getStatus() != FoodProductReviewCaseStatus.IN_REVIEW) {
            throw new IllegalStateException("Product intake evidence cannot be decided in its current state.");
        }
        reviewCase.setStatus(approved ? FoodProductReviewCaseStatus.APPROVED : FoodProductReviewCaseStatus.REJECTED);
        reviewCase.setReviewNote(note.trim());
        reviewCase.setReviewedBy(actor.getEmail());
        reviewCase.setReviewedAt(LocalDateTime.now());
        if (approved) {
            if (evidenceService == null) throw new IllegalStateException("Review evidence service is unavailable.");
            evidenceService.recordAcceptedEvidence(reviewCase);
            if (catalogMediaService != null) {
                catalogMediaService.promoteApprovedFrontImage(reviewCase, reviewCase.getFoodItem());
            }
        }
        FoodProductReviewCaseEntity saved = repository.save(reviewCase);
        if (evidenceExpiryScheduler != null) {
            evidenceExpiryScheduler.schedule(saved.getId(), saved.getStatus(), saved.getReviewedAt());
        }
        if (!approved) notifyRejectedDecision(saved);
        return action(saved);
    }

    @Override
    @Transactional
    public AdminProductIntakeActionDto attachExistingProduct(Long caseId, String actorEmail, Long foodItemId) {
        UserEntity actor = requireActiveAdmin(actorEmail);
        FoodProductReviewCaseEntity reviewCase = lockedCase(caseId);
        requireAssignedOrOwner(reviewCase, actor);
        var foodItem = foodItemRepository.findById(foodItemId)
                .orElseThrow(() -> new IllegalArgumentException("Food item was not found."));
        reviewCase.setFoodItem(foodItem);
        reviewCase.setResolutionMode(FoodProductResolutionMode.UPDATE_EXISTING);
        return action(repository.save(reviewCase));
    }

    @Override
    @Transactional
    public AdminProductIntakeActionDto publishCandidate(Long caseId, String actorEmail, String reason, String correlationId, boolean confirmed) {
        UserEntity actor = requireActiveAdmin(actorEmail);
        FoodProductReviewCaseEntity reviewCase = lockedCase(caseId);
        requireAssignedOrOwner(reviewCase, actor);
        if (!confirmed) throw new IllegalArgumentException("Explicit publication confirmation is required.");
        if (reviewCase.getStatus() != FoodProductReviewCaseStatus.APPROVED) throw new IllegalStateException("Only an approved product intake can be published.");
        if (reviewCase.getResolutionMode() != FoodProductResolutionMode.NEW_CANDIDATE) throw new IllegalStateException("Only a new candidate can use candidate publication.");
        var food = reviewCase.getFoodItem();
        if (food == null || food.getPublicationStatus() != com.grun.calorietracker.enums.CatalogPublicationStatus.INTERNAL_REVIEW) throw new IllegalStateException("Candidate must still be internal review.");
        if (catalogPublicationService == null) throw new IllegalStateException("Catalog publication service is unavailable.");
        if (catalogMutationOrchestrator == null) throw new IllegalStateException("Catalog mutation orchestrator is unavailable.");
        String previousCanonicalKey = food.getCanonicalFoodKey();
        Map<String, Object> oldValues = new LinkedHashMap<>();
        oldValues.put("verificationStatus", food.getVerificationStatus());
        oldValues.put("publicationStatus", food.getPublicationStatus());
        food.setVerificationStatus(com.grun.calorietracker.enums.VerificationStatus.VERIFIED);
        applyGate.requirePublishable(reviewCase, food);
        var published = catalogPublicationService.publish(food.getId(), actor.getEmail(), reason, correlationId);
        if (published.getPublicationStatus() != com.grun.calorietracker.enums.CatalogPublicationStatus.PUBLISHED) throw new IllegalStateException("Central publication did not publish the candidate.");
        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("verificationStatus", published.getVerificationStatus());
        newValues.put("publicationStatus", published.getPublicationStatus());
        catalogMutationOrchestrator.reconcileAndAudit(published, previousCanonicalKey, actor.getEmail(), reviewCase.getId(), oldValues, newValues);
        reviewCase.setStatus(FoodProductReviewCaseStatus.APPLIED);
        reviewCase.setAppliedAt(LocalDateTime.now());
        FoodProductReviewCaseEntity saved = repository.save(reviewCase);
        notifySuccessfulDecision(saved, true);
        intakeMetrics.record("publish", "success");
        return action(saved);
    }
    @Override
    @Transactional
    public AdminProductIntakeActionDto applyExistingProduct(Long caseId, String actorEmail, Set<ProductIntakeApplyField> fields, boolean confirmed) {
        UserEntity actor = requireActiveAdmin(actorEmail);
        FoodProductReviewCaseEntity reviewCase = lockedCase(caseId);
        requireAssignedOrOwner(reviewCase, actor);
        if (reviewCase.getStatus() != FoodProductReviewCaseStatus.APPROVED) throw new IllegalStateException("Only an approved product intake can be applied.");
        if (reviewCase.getResolutionMode() != FoodProductResolutionMode.UPDATE_EXISTING) throw new IllegalStateException("This action applies only to an existing product.");
        if (fields == null || fields.isEmpty()) throw new IllegalArgumentException("At least one field must be selected.");
        var food = reviewCase.getFoodItem();
        if (food == null || food.getPublicationStatus() != com.grun.calorietracker.enums.CatalogPublicationStatus.PUBLISHED) throw new IllegalStateException("Existing-product apply requires a published target.");
        if (catalogMutationOrchestrator == null) throw new IllegalStateException("Catalog mutation orchestrator is unavailable.");
        applyGate.requireAcceptedEvidence(reviewCase);
        Map<String, Object> submitted = submittedFields(reviewCase.getSubmittedValuesJson(), new ArrayList<>());
        EnumMap<ProductIntakeApplyField, Object> values = new EnumMap<>(ProductIntakeApplyField.class);
        for (ProductIntakeApplyField field : fields) values.put(field, validatedApplyValue(field, submitted));
        boolean highImpact = values.entrySet().stream().anyMatch(entry -> isHighImpact(entry.getKey(), currentValue(food, entry.getKey()), entry.getValue()));
        if (highImpact && !confirmed) throw new IllegalArgumentException("Explicit confirmation is required for material nutrition changes.");
        String previousCanonicalKey = food.getCanonicalFoodKey();
        Map<String, Object> oldValues = new LinkedHashMap<>();
        values.keySet().forEach(field -> oldValues.put(applyFieldName(field), currentValue(food, field)));
        values.forEach((field, value) -> applyValue(food, field, value));
        applyGate.requireProductQuality(food);
        foodItemRepository.save(food);
        Map<String, Object> newValues = new LinkedHashMap<>();
        values.keySet().forEach(field -> newValues.put(applyFieldName(field), currentValue(food, field)));
        catalogMutationOrchestrator.reconcileAndAudit(food, previousCanonicalKey, actor.getEmail(), reviewCase.getId(), oldValues, newValues);
        reviewCase.setStatus(FoodProductReviewCaseStatus.APPLIED);
        reviewCase.setAppliedAt(LocalDateTime.now());
        FoodProductReviewCaseEntity saved = repository.save(reviewCase);
        notifySuccessfulDecision(saved, false);
        intakeMetrics.record("apply_existing", "success");
        return action(saved);
    }

    private Object validatedApplyValue(ProductIntakeApplyField field, Map<String, Object> submitted) {
        String key = field == ProductIntakeApplyField.PRODUCT_NAME ? "productName" : field.name().toLowerCase();
        if (!submitted.containsKey(key)) throw new IllegalArgumentException("Selected field is missing from the accepted submission: " + field);
        Object raw = submitted.get(key);
        if (field == ProductIntakeApplyField.PRODUCT_NAME) {
            if (!(raw instanceof String text) || text.isBlank()) throw new IllegalArgumentException("Product name cannot be blank.");
            return text.trim();
        }
        if (field == ProductIntakeApplyField.BRAND) return raw == null || raw.toString().isBlank() ? null : raw.toString().trim();
        Double value;
        try { value = raw instanceof Number number ? number.doubleValue() : Double.valueOf(raw.toString().replace(',', '.')); }
        catch (RuntimeException failure) { throw new IllegalArgumentException("Selected nutrition field is not numeric: " + field, failure); }
        if (value < 0 || value.isNaN() || value.isInfinite()) throw new IllegalArgumentException("Selected nutrition field is invalid: " + field);
        return value;
    }

    private void applyValue(com.grun.calorietracker.entity.FoodItemEntity food, ProductIntakeApplyField field, Object value) {
        switch (field) {
            case PRODUCT_NAME -> food.setName((String) value);
            case BRAND -> food.setBrand((String) value);
            case CALORIES -> food.setCalories((Double) value);
            case PROTEIN -> food.setProtein((Double) value);
            case FAT -> food.setFat((Double) value);
            case CARBS -> food.setCarbs((Double) value);
            case FIBER -> food.setFiber((Double) value);
            case SUGAR -> food.setSugar((Double) value);
            case SODIUM -> food.setSodium((Double) value);
        }
    }
    private String applyFieldName(ProductIntakeApplyField field) {
        return field == ProductIntakeApplyField.PRODUCT_NAME ? "name" : field.name().toLowerCase();
    }

    private Object currentValue(com.grun.calorietracker.entity.FoodItemEntity food, ProductIntakeApplyField field) {
        return switch (field) {
            case PRODUCT_NAME -> food.getName();
            case BRAND -> food.getBrand();
            case CALORIES -> food.getCalories();
            case PROTEIN -> food.getProtein();
            case FAT -> food.getFat();
            case CARBS -> food.getCarbs();
            case FIBER -> food.getFiber();
            case SUGAR -> food.getSugar();
            case SODIUM -> food.getSodium();
        };
    }
    @Override
    @Transactional
    public AdminProductIntakeActionDto createManual(String actorEmail, AdminProductIntakeManualRequestDto request) {
        UserEntity actor = requireActiveAdmin(actorEmail);
        if (actor.getRole() != UserRole.OWNER && actor.getRole() != UserRole.ADMIN_CATALOG) {
            throw new AccessDeniedException("Owner or catalog-admin access required.");
        }
        FoodProductReviewCaseEntity reviewCase = reviewCaseService.finalizeCase(new FoodProductReviewCaseCommand(
                request.idempotencyKey(),
                FoodProductReviewCaseSource.ADMIN_MANUAL,
                actor.getEmail(),
                null,
                request.barcode(),
                request.marketRegion(),
                null,
                request.productName(),
                request.brand(),
                request.calories(),
                request.protein(),
                request.fat(),
                request.carbs(),
                request.fiber(),
                request.sugar(),
                request.sodium(),
                request.nutritionBasis(),
                FoodProductReviewRiskLevel.MEDIUM,
                1,
                submittedValues(request),
                null,
                null,
                null,
                false,
                false
        ));
        if (reviewCase.getFoodItem() == null
                || reviewCase.getFoodItem().getPublicationStatus()
                != com.grun.calorietracker.enums.CatalogPublicationStatus.INTERNAL_REVIEW) {
            throw new IllegalStateException("Admin manual intake must remain an internal-review candidate.");
        }
        return action(reviewCase);
    }

    private String submittedValues(AdminProductIntakeManualRequestDto request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Manual product intake could not be serialized.", failure);
        }
    }
    @Override
    @Transactional(readOnly = true)
    public AdminProductIntakeDetailDto detail(Long caseId) {
        FoodProductReviewCaseEntity reviewCase = repository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Product intake was not found."));
        List<FoodProductReviewCaseAssetEntity> assets = assetRepository
                .findAllByReviewCaseIdOrderByAssetTypeAsc(caseId);
        List<String> warnings = new ArrayList<>();
        Map<String, Object> submitted = submittedFields(reviewCase.getSubmittedValuesJson(), warnings);
        Map<String, Object> catalog = catalogFields(reviewCase);
        if (reviewCase.getRiskLevel() == FoodProductReviewRiskLevel.HIGH) warnings.add("HIGH_RISK");
        if (assets.isEmpty()) warnings.add("NO_EVIDENCE");
        LocalDateTime now = LocalDateTime.now();
        var evidence = assets.stream().map(asset -> {
            boolean available = asset.getUploadState() == com.grun.calorietracker.enums.FoodProductAssetUploadState.VERIFIED
                    && asset.getDeletionState() == com.grun.calorietracker.enums.FoodProductAssetDeletionState.ACTIVE
                    && asset.getExpiresAt() != null && asset.getExpiresAt().isAfter(now);
            if (!available) warnings.add("EVIDENCE_UNAVAILABLE:" + asset.getAssetType());
            return new AdminProductIntakeDetailDto.EvidenceDescriptor(asset.getId(), asset.getAssetType(),
                    asset.getContentType(), asset.getSizeBytes(), asset.getWidth(), asset.getHeight(),
                    asset.getUploadState(), asset.getDeletionState(), asset.getExpiresAt(), available);
        }).toList();
        LocalDateTime expiry = assets.stream().map(FoodProductReviewCaseAssetEntity::getExpiresAt)
                .filter(Objects::nonNull).min(LocalDateTime::compareTo).orElse(null);
        return new AdminProductIntakeDetailDto(toSummary(reviewCase), reviewCase.getReviewNote(),
                reviewCase.getFoodItem() == null ? null : reviewCase.getFoodItem().getId(),
                reviewCase.getFoodItem() == null ? null : reviewCase.getFoodItem().getPublicationStatus(),
                submitted, catalog, comparisons(submitted, catalog), List.copyOf(warnings), expiry, evidence,
                corroboratingEvidence(reviewCase));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> submittedFields(String json, List<String> warnings) {
        try {
            if (json == null || json.isBlank()) return Map.of();
            return objectMapper.readValue(json, LinkedHashMap.class);
        } catch (JsonProcessingException failure) {
            warnings.add("SUBMITTED_VALUES_INVALID");
            return Map.of();
        }
    }

    private Map<String, Object> catalogFields(FoodProductReviewCaseEntity reviewCase) {
        if (reviewCase.getFoodItem() == null) return Map.of();
        var food = reviewCase.getFoodItem();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("productName", food.getName());
        values.put("brand", food.getBrand());
        values.put("calories", food.getCalories());
        values.put("protein", food.getProtein());
        values.put("fat", food.getFat());
        values.put("carbs", food.getCarbs());
        values.put("fiber", food.getFiber());
        values.put("sugar", food.getSugar());
        values.put("sodium", food.getSodium());
        values.put("nutritionBasis", food.getNutritionBasis());
        return values;
    }

    private List<AdminProductIntakeDetailDto.FieldComparison> comparisons(
            Map<String, Object> submitted, Map<String, Object> catalog
    ) {
        TreeSet<String> fields = new TreeSet<>();
        fields.addAll(submitted.keySet());
        fields.addAll(catalog.keySet());
        return fields.stream().map(field -> new AdminProductIntakeDetailDto.FieldComparison(field,
                submitted.get(field), catalog.get(field), Objects.equals(submitted.get(field), catalog.get(field)),
                isHighImpactField(field, catalog.get(field), submitted.get(field))))
                .toList();
    }
    private boolean isHighImpact(ProductIntakeApplyField field, Object oldValue, Object newValue) {
        return switch (field) {
            case CALORIES, PROTEIN, FAT, CARBS, FIBER, SUGAR, SODIUM -> materialDifference(oldValue, newValue);
            default -> false;
        };
    }

    private boolean isHighImpactField(String field, Object oldValue, Object newValue) {
        try {
            ProductIntakeApplyField applyField = "productName".equals(field)
                    ? ProductIntakeApplyField.PRODUCT_NAME
                    : ProductIntakeApplyField.valueOf(field.toUpperCase());
            return isHighImpact(applyField, oldValue, newValue);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean materialDifference(Object oldValue, Object newValue) {
        Double oldNumber = numberValue(oldValue);
        Double newNumber = numberValue(newValue);
        if (oldNumber == null || newNumber == null || Objects.equals(oldNumber, newNumber)) return false;
        if (oldNumber == 0.0) return newNumber != 0.0;
        return Math.abs(newNumber - oldNumber) / Math.abs(oldNumber) >= 0.20;
    }

    private Double numberValue(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return null;
        try { return Double.valueOf(value.toString().replace(',', '.')); }
        catch (NumberFormatException ignored) { return null; }
    }

    private List<AdminProductIntakeDetailDto.CorroboratingEvidence> corroboratingEvidence(FoodProductReviewCaseEntity reviewCase) {
        if (sourceEvidenceRepository == null || reviewCase.getFoodItem() == null || reviewCase.getFoodItem().getId() == null) return List.of();
        return sourceEvidenceRepository.findByFoodItemIdOrderByObservedAtDescIdDesc(reviewCase.getFoodItem().getId()).stream()
                .map(value -> new AdminProductIntakeDetailDto.CorroboratingEvidence(value.getFieldName(), value.getProvider(),
                        value.getNumericValue(), value.getBasis(), value.getConfidenceScore(), value.getObservedAt(), value.getSourceVersion()))
                .toList();
    }

    private void notifySuccessfulDecision(FoodProductReviewCaseEntity reviewCase, boolean publication) {
        if (reviewCase.getSubmittedBy() == null || reviewCase.getStatus() != FoodProductReviewCaseStatus.APPLIED) return;
        notificationRepository.save(decisionNotification(reviewCase,
                publication ? "Product contribution published" : "Product contribution applied",
                publication ? "Your contribution is now available in the product catalog." : "Your verified contribution improved an existing catalog product.",
                "VIEW_APPLIED_PRODUCT", "SUCCESS"));
    }

    private void notifyRejectedDecision(FoodProductReviewCaseEntity reviewCase) {
        if (reviewCase.getSubmittedBy() == null || reviewCase.getStatus() != FoodProductReviewCaseStatus.REJECTED) return;
        notificationRepository.save(decisionNotification(reviewCase, "Product contribution reviewed",
                "Your product contribution was not applied to the catalog.", "VIEW_PRODUCT_CONTRIBUTION", "INFO"));
    }

    private NotificationEntity decisionNotification(FoodProductReviewCaseEntity reviewCase, String title, String message,
                                                    String action, String severity) {
        NotificationEntity notification = new NotificationEntity();
        notification.setUser(reviewCase.getSubmittedBy());
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setNote(reviewCase.getReviewNote());
        notification.setType("PRODUCT_INTAKE");
        notification.setSeverity(severity);
        notification.setSource("PRODUCT_INTAKE");
        notification.setTargetType(reviewCase.getFoodItem() == null ? "FOOD_PRODUCT_REVIEW_CASE" : "FOOD_ITEM");
        notification.setTargetId(reviewCase.getFoodItem() == null ? reviewCase.getId().toString() : reviewCase.getFoodItem().getId().toString());
        notification.setTargetRoute(reviewCase.getFoodItem() == null ? "product-contribution-review" : "food-detail");
        notification.setPrimaryAction(action);
        notification.setVisibleInApp(true);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        return notification;
    }
    private void requireAssignedOrOwner(FoodProductReviewCaseEntity reviewCase, UserEntity actor) {
        if (actor.getRole() == UserRole.OWNER) return;
        if (actor.getRole() != UserRole.ADMIN_CATALOG
                || reviewCase.getAssignedAdminEmail() == null
                || !reviewCase.getAssignedAdminEmail().equalsIgnoreCase(actor.getEmail())) {
            throw new AccessDeniedException("Product intake action requires the assigned catalog admin or owner.");
        }
    }

    private NotificationEntity evidenceNotification(FoodProductReviewCaseEntity reviewCase, String note) {
        NotificationEntity notification = new NotificationEntity();
        notification.setUser(reviewCase.getSubmittedBy());
        notification.setTitle("More product evidence needed");
        notification.setMessage("Please update the requested product evidence.");
        notification.setNote(note.trim());
        notification.setType("PRODUCT_INTAKE");
        notification.setSeverity("INFO");
        notification.setSource("PRODUCT_INTAKE");
        notification.setTargetType("FOOD_PRODUCT_REVIEW_CASE");
        notification.setTargetId(reviewCase.getId().toString());
        notification.setTargetRoute("product-contribution-review");
        notification.setPrimaryAction("UPDATE_PRODUCT_EVIDENCE");
        notification.setVisibleInApp(true);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        return notification;
    }

    private AdminProductIntakeActionDto action(FoodProductReviewCaseEntity reviewCase) {
        return new AdminProductIntakeActionDto(reviewCase.getId(), reviewCase.getStatus(),
                reviewCase.getFoodItem() == null ? null : reviewCase.getFoodItem().getId(),
                reviewCase.getAssignedAdminEmail());
    }
    private FoodProductReviewCaseEntity lockedCase(Long caseId) {
        return repository.findByIdForAssignment(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Product intake was not found."));
    }

    private UserEntity requireActiveCatalogAdmin(String email) {
        UserEntity user = requireActiveAdmin(email);
        if (user.getRole() != UserRole.ADMIN_CATALOG) {
            throw new AccessDeniedException("Active catalog-admin account required.");
        }
        return user;
    }

    private UserEntity requireActiveAdmin(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("Active admin account required."));
        if (!Boolean.TRUE.equals(user.getAccountEnabled()) || user.getRole() == null || !user.getRole().isAdminRole()) {
            throw new AccessDeniedException("Active admin account required.");
        }
        return user;
    }

    private AdminProductIntakeAssignmentDto assignment(FoodProductReviewCaseEntity reviewCase) {
        return new AdminProductIntakeAssignmentDto(reviewCase.getId(), reviewCase.getAssignedAdminEmail(),
                reviewCase.getReviewClaimedAt());
    }
    private Specification<FoodProductReviewCaseEntity> queueSpecification(
            AdminProductIntakeQueue queue,
            String adminEmail
    ) {
        return switch (queue) {
            case ALL -> (root, query, cb) -> cb.conjunction();
            case MY_QUEUE -> (root, query, cb) -> cb.equal(root.get("assignedAdminEmail"), adminEmail);
            case UNASSIGNED -> (root, query, cb) -> cb.isNull(root.get("assignedAdminEmail"));
            case NEEDS_ACTION -> (root, query, cb) -> cb.equal(root.get("status"),
                    FoodProductReviewCaseStatus.NEEDS_SUBMITTER_ACTION);
            case HIGH_RISK -> (root, query, cb) -> cb.equal(root.get("riskLevel"),
                    FoodProductReviewRiskLevel.HIGH);
            case OVERDUE -> (root, query, cb) -> cb.and(
                    root.get("status").in(OPEN_STATUSES),
                    cb.lessThan(root.get("createdAt"), LocalDateTime.now().minusHours(overdueHours))
            );
        };
    }

    private AdminProductIntakeSummaryDto toSummary(FoodProductReviewCaseEntity entity) {
        return new AdminProductIntakeSummaryDto(entity.getId(), entity.getSource(), entity.getStatus(),
                entity.getMarketRegion(), entity.getNormalizedBarcode(), entity.getResolutionMode(),
                entity.getRiskLevel(), entity.getAssignedAdminEmail(), entity.getReviewClaimedAt(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}