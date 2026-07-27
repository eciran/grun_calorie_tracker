package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminCatalogImportJobDto;
import com.grun.calorietracker.dto.AdminCatalogSummaryDto;
import com.grun.calorietracker.dto.CatalogReviewAssignmentRequestDto;
import com.grun.calorietracker.dto.ExerciseItemDto;
import com.grun.calorietracker.dto.ExerciseItemPageDto;
import com.grun.calorietracker.dto.ExerciseTechniqueReviewRequestDto;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.ProductQualityScanRunEntity;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.RecipeImportCandidateEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.ExerciseDifficulty;
import com.grun.calorietracker.enums.ExerciseTechniqueReviewStatus;
import com.grun.calorietracker.enums.RecipeImportCandidateStatus;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.mapper.ExerciseItemMapper;
import com.grun.calorietracker.repository.ExerciseItemRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.ProductQualityScanRunRepository;
import com.grun.calorietracker.repository.RecipeImportCandidateRepository;
import com.grun.calorietracker.repository.RecipeRepository;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.AdminCatalogOperationsService;
import com.grun.calorietracker.service.ExerciseItemService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminCatalogOperationsServiceImpl implements AdminCatalogOperationsService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int FRESHNESS_DAYS = 120;

    private final FoodItemRepository foodItemRepository;
    private final RecipeRepository recipeRepository;
    private final ExerciseItemRepository exerciseItemRepository;
    private final ProductQualityScanRunRepository scanRunRepository;
    private final RecipeImportCandidateRepository recipeImportCandidateRepository;
    private final ExerciseItemMapper exerciseItemMapper;
    private final ExerciseItemService exerciseItemService;
    private final AdminAuditService adminAuditService;

    @Override
    @Transactional(readOnly = true)
    public AdminCatalogSummaryDto summary() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleCutoff = now.minusDays(FRESHNESS_DAYS);
        long foodTotal = foodItemRepository.count();
        long foodApproved = foodItemRepository.countByVerificationStatus(VerificationStatus.VERIFIED);
        long foodPending = foodItemRepository.countByVerificationStatus(VerificationStatus.RAW_IMPORTED)
                + foodItemRepository.countByVerificationStatus(VerificationStatus.NEEDS_REVIEW);
        long recipeTotal = recipeRepository.countByArchivedFalse();
        long recipeApproved = recipeRepository.countByVerificationStatusAndArchivedFalse(VerificationStatus.VERIFIED);
        long recipePending = recipeRepository.countByVerificationStatusAndArchivedFalse(VerificationStatus.NEEDS_REVIEW);
        long exerciseTotal = exerciseItemRepository.count();
        long exerciseApproved = exerciseItemRepository.countByTechniqueReviewStatus(ExerciseTechniqueReviewStatus.APPROVED);
        long exercisePending = exerciseItemRepository.countByTechniqueReviewStatus(ExerciseTechniqueReviewStatus.PENDING)
                + exerciseItemRepository.countByTechniqueReviewStatus(ExerciseTechniqueReviewStatus.IN_REVIEW);

        List<AdminCatalogSummaryDto.SourceSummary> sources = foodItemRepository.summarizeSources(staleCutoff).stream()
                .map(row -> new AdminCatalogSummaryDto.SourceSummary(
                        String.valueOf(row[0]),
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue(),
                        0
                ))
                .toList();

        return new AdminCatalogSummaryDto(
                new AdminCatalogSummaryDto.CatalogTypeSummary(
                        foodTotal, foodApproved, foodPending, foodItemRepository.countMissingApprovedMedia(),
                        foodItemRepository.countStaleCatalogItems(staleCutoff),
                        foodItemRepository.countByReviewDueAtBefore(now)
                ),
                new AdminCatalogSummaryDto.CatalogTypeSummary(
                        recipeTotal, recipeApproved, recipePending, recipeRepository.countMissingApprovedMedia(),
                        0, recipeRepository.countByReviewDueAtBeforeAndArchivedFalse(now)
                ),
                new AdminCatalogSummaryDto.CatalogTypeSummary(
                        exerciseTotal, exerciseApproved, exercisePending, exerciseItemRepository.countMissingMedia(),
                        exerciseItemRepository.countStaleSourceEvidence(staleCutoff),
                        exerciseItemRepository.countByReviewDueAtBefore(now)
                ),
                sources
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminCatalogImportJobDto> recentImportJobs() {
        List<AdminCatalogImportJobDto> jobs = new ArrayList<>();
        Page<ProductQualityScanRunEntity> scans = scanRunRepository.findAllByOrderByStartedAtDesc(
                PageRequest.of(0, 20)
        );
        scans.forEach(run -> jobs.add(new AdminCatalogImportJobDto(
                "FOOD-SCAN-" + run.getId(),
                "FOOD",
                run.getSource().name(),
                run.getTriggerType().name(),
                run.getMarketRegion() == null ? null : run.getMarketRegion().name(),
                run.getStatus().name(),
                run.getScannedProducts(),
                run.getCreatedSuggestions(),
                "Internal quality evidence",
                run.getStatus().name().equals("FAILED"),
                run.getErrorMessage(),
                run.getStartedAt(),
                run.getCompletedAt()
        )));

        Map<String, List<RecipeImportCandidateEntity>> batches = new LinkedHashMap<>();
        recipeImportCandidateRepository.findTop100ByOrderByCreatedAtDesc()
                .forEach(item -> batches.computeIfAbsent(item.getBatchId(), ignored -> new ArrayList<>()).add(item));
        batches.forEach((batchId, items) -> {
            RecipeImportCandidateEntity first = items.get(0);
            long issues = items.stream().filter(item -> item.getUnresolvedIngredientCount() != null
                    && item.getUnresolvedIngredientCount() > 0).count();
            String status = items.stream().anyMatch(item -> item.getStatus() == RecipeImportCandidateStatus.FAILED)
                    ? "FAILED"
                    : items.stream().anyMatch(item -> item.getStatus() == RecipeImportCandidateStatus.PENDING)
                    ? "PENDING"
                    : "COMPLETED";
            jobs.add(new AdminCatalogImportJobDto(
                    "RECIPE-" + batchId,
                    "RECIPE",
                    first.getSourceTitle(),
                    "BATCH_IMPORT",
                    first.getMarketRegion() == null ? null : first.getMarketRegion().name(),
                    status,
                    items.size(),
                    issues,
                    first.getLicense(),
                    false,
                    "FAILED".equals(status) ? "One or more candidates failed validation." : null,
                    items.stream().map(RecipeImportCandidateEntity::getCreatedAt).min(LocalDateTime::compareTo).orElse(null),
                    items.stream().map(RecipeImportCandidateEntity::getUpdatedAt).max(LocalDateTime::compareTo).orElse(null)
            ));
        });
        return jobs.stream()
                .sorted(Comparator.comparing(AdminCatalogImportJobDto::startedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(25)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ExerciseItemPageDto searchExercises(String query,
                                               ExerciseDifficulty difficulty,
                                               Boolean active,
                                               ExerciseTechniqueReviewStatus reviewStatus,
                                               String assignee,
                                               int page,
                                               int size) {
        Page<ExerciseItemEntity> result = exerciseItemRepository.findAll(
                exerciseSpecification(query, difficulty, active, reviewStatus, assignee),
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), Sort.by("name").ascending())
        );
        ExerciseItemPageDto response = new ExerciseItemPageDto();
        response.setContent(result.map(exerciseItemMapper::toDto).getContent());
        response.setPage(result.getNumber());
        response.setSize(result.getSize());
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        response.setFirst(result.isFirst());
        response.setLast(result.isLast());
        return response;
    }

    @Override
    @Transactional
    public ExerciseItemDto createExercise(String adminEmail, ExerciseItemDto request) {
        request.setId(null);
        request.setAiEligible(false);
        request.setTechniqueReviewStatus(ExerciseTechniqueReviewStatus.PENDING);
        request.setTechniqueReviewedAt(null);
        request.setTechniqueReviewedBy(null);
        ExerciseItemDto created = exerciseItemService.addItem(request);
        audit(adminEmail, AdminAuditActionType.CATALOG_EXERCISE_CREATE, created.getId(), null,
                exerciseAuditValue(created));
        return created;
    }

    @Override
    @Transactional
    public ExerciseItemDto updateExercise(String adminEmail, Long id, ExerciseItemDto request) {
        ExerciseItemEntity existing = requireExercise(id);
        Map<String, Object> oldValue = exerciseAuditValue(exerciseItemMapper.toDto(existing));
        preserveControlledReviewFields(existing, request);
        ExerciseItemDto updated = exerciseItemService.updateItem(id, request);
        audit(adminEmail, AdminAuditActionType.CATALOG_EXERCISE_UPDATE, id, oldValue,
                exerciseAuditValue(updated));
        return updated;
    }

    @Override
    @Transactional
    public ExerciseItemDto reviewExercise(String adminEmail, Long id, ExerciseTechniqueReviewRequestDto request) {
        ExerciseItemEntity exercise = requireExercise(id);
        Map<String, Object> oldValue = Map.of(
                "status", exercise.getTechniqueReviewStatus().name(),
                "reviewedBy", String.valueOf(exercise.getTechniqueReviewedBy())
        );
        exercise.setTechniqueReviewStatus(request.status());
        exercise.setTechniqueReviewNote(request.note().trim());
        exercise.setTechniqueReviewedBy(adminEmail);
        exercise.setTechniqueReviewedAt(LocalDateTime.now());
        if (request.status() != ExerciseTechniqueReviewStatus.APPROVED) {
            exercise.setAiEligible(false);
        }
        ExerciseItemDto updated = exerciseItemMapper.toDto(exerciseItemRepository.save(exercise));
        audit(adminEmail, AdminAuditActionType.CATALOG_EXERCISE_REVIEW, id, oldValue,
                Map.of("status", request.status().name(), "note", request.note().trim()));
        return updated;
    }

    @Override
    @Transactional
    public void assignReview(String adminEmail,
                             String itemType,
                             Long itemId,
                             CatalogReviewAssignmentRequestDto request) {
        if (request.dueAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Review dueAt must be in the future.");
        }
        String normalizedType = itemType.trim().toUpperCase(Locale.ROOT);
        String assignee = normalizeAssignee(request.assignee());
        Map<String, Object> oldValue;
        switch (normalizedType) {
            case "FOOD" -> {
                FoodItemEntity item = foodItemRepository.findById(itemId)
                        .orElseThrow(() -> new ResourceNotFoundException("Food item not found: " + itemId));
                oldValue = assignmentValue(item.getReviewAssignee(), item.getReviewDueAt());
                applyAssignment(item, assignee, request.dueAt());
                foodItemRepository.save(item);
            }
            case "RECIPE" -> {
                RecipeEntity item = recipeRepository.findById(itemId)
                        .orElseThrow(() -> new ResourceNotFoundException("Recipe not found: " + itemId));
                oldValue = assignmentValue(item.getReviewAssignee(), item.getReviewDueAt());
                applyAssignment(item, assignee, request.dueAt());
                recipeRepository.save(item);
            }
            case "EXERCISE" -> {
                ExerciseItemEntity item = requireExercise(itemId);
                oldValue = assignmentValue(item.getReviewAssignee(), item.getReviewDueAt());
                applyAssignment(item, assignee, request.dueAt());
                exerciseItemRepository.save(item);
            }
            default -> throw new IllegalArgumentException("Unsupported catalog item type: " + normalizedType);
        }
        Map<String, Object> newValue = new LinkedHashMap<>(assignmentValue(assignee, request.dueAt()));
        newValue.put("reason", request.reason().trim());
        adminAuditService.record(adminEmail, AdminAuditActionType.CATALOG_REVIEW_ASSIGNMENT,
                AdminAuditTargetType.CATALOG_REVIEW_ITEM, normalizedType + ":" + itemId,
                oldValue, newValue, MDC.get("correlationId"));
    }

    private Specification<ExerciseItemEntity> exerciseSpecification(String query,
                                                                    ExerciseDifficulty difficulty,
                                                                    Boolean active,
                                                                    ExerciseTechniqueReviewStatus reviewStatus,
                                                                    String assignee) {
        return (root, ignored, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern),
                        builder.like(builder.lower(root.get("metCode")), pattern),
                        builder.like(builder.lower(root.get("primaryMuscleGroup")), pattern)
                ));
            }
            if (difficulty != null) predicates.add(builder.equal(root.get("difficulty"), difficulty));
            if (active != null) predicates.add(builder.equal(root.get("active"), active));
            if (reviewStatus != null) predicates.add(builder.equal(root.get("techniqueReviewStatus"), reviewStatus));
            if (assignee != null && !assignee.isBlank()) {
                predicates.add(builder.equal(builder.lower(root.get("reviewAssignee")),
                        assignee.trim().toLowerCase(Locale.ROOT)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private ExerciseItemEntity requireExercise(Long id) {
        return exerciseItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exercise item not found: " + id));
    }

    private void preserveControlledReviewFields(ExerciseItemEntity existing, ExerciseItemDto request) {
        request.setTechniqueReviewStatus(existing.getTechniqueReviewStatus());
        request.setTechniqueReviewNote(existing.getTechniqueReviewNote());
        request.setTechniqueReviewedBy(existing.getTechniqueReviewedBy());
        request.setTechniqueReviewedAt(existing.getTechniqueReviewedAt());
        request.setReviewAssignee(existing.getReviewAssignee());
        request.setReviewDueAt(existing.getReviewDueAt());
        request.setReviewClaimedAt(existing.getReviewClaimedAt());
    }

    private void applyAssignment(FoodItemEntity item, String assignee, LocalDateTime dueAt) {
        item.setReviewAssignee(assignee);
        item.setReviewDueAt(dueAt);
        item.setReviewClaimedAt(LocalDateTime.now());
    }

    private void applyAssignment(RecipeEntity item, String assignee, LocalDateTime dueAt) {
        item.setReviewAssignee(assignee);
        item.setReviewDueAt(dueAt);
        item.setReviewClaimedAt(LocalDateTime.now());
    }

    private void applyAssignment(ExerciseItemEntity item, String assignee, LocalDateTime dueAt) {
        item.setReviewAssignee(assignee);
        item.setReviewDueAt(dueAt);
        item.setReviewClaimedAt(LocalDateTime.now());
        if (item.getTechniqueReviewStatus() == ExerciseTechniqueReviewStatus.PENDING) {
            item.setTechniqueReviewStatus(ExerciseTechniqueReviewStatus.IN_REVIEW);
        }
    }

    private String normalizeAssignee(String assignee) {
        return assignee == null || assignee.isBlank() ? null : assignee.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> assignmentValue(String assignee, LocalDateTime dueAt) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("assignee", assignee);
        value.put("dueAt", dueAt);
        return value;
    }

    private Map<String, Object> exerciseAuditValue(ExerciseItemDto item) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", item.getName());
        value.put("metCode", item.getMetCode());
        value.put("active", item.getActive());
        value.put("aiEligible", item.getAiEligible());
        value.put("techniqueReviewStatus", item.getTechniqueReviewStatus());
        value.put("sourceName", item.getSourceName());
        value.put("licenseName", item.getLicenseName());
        return value;
    }

    private void audit(String adminEmail,
                       AdminAuditActionType action,
                       Long id,
                       Object oldValue,
                       Object newValue) {
        adminAuditService.record(adminEmail, action, AdminAuditTargetType.EXERCISE_ITEM,
                String.valueOf(id), oldValue, newValue, MDC.get("correlationId"));
    }
}
