package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.CatalogReviewAssignmentRequestDto;
import com.grun.calorietracker.dto.ExerciseItemDto;
import com.grun.calorietracker.dto.ExerciseTechniqueReviewRequestDto;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.ExerciseTechniqueReviewStatus;
import com.grun.calorietracker.mapper.ExerciseItemMapper;
import com.grun.calorietracker.repository.ExerciseItemRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.ProductQualityScanRunRepository;
import com.grun.calorietracker.repository.RecipeImportCandidateRepository;
import com.grun.calorietracker.repository.RecipeRepository;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.ExerciseItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCatalogOperationsServiceImplTest {

    @Mock private FoodItemRepository foodItemRepository;
    @Mock private RecipeRepository recipeRepository;
    @Mock private ExerciseItemRepository exerciseItemRepository;
    @Mock private ProductQualityScanRunRepository scanRunRepository;
    @Mock private RecipeImportCandidateRepository recipeImportCandidateRepository;
    @Mock private ExerciseItemMapper exerciseItemMapper;
    @Mock private ExerciseItemService exerciseItemService;
    @Mock private AdminAuditService adminAuditService;

    private AdminCatalogOperationsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminCatalogOperationsServiceImpl(
                foodItemRepository, recipeRepository, exerciseItemRepository,
                scanRunRepository, recipeImportCandidateRepository,
                exerciseItemMapper, exerciseItemService, adminAuditService
        );
    }

    @Test
    void createExerciseAlwaysStartsInPendingReviewAndIsAudited() {
        ExerciseItemDto request = exercise("RUN_TEST");
        request.setId(99L);
        request.setTechniqueReviewStatus(ExerciseTechniqueReviewStatus.APPROVED);
        ExerciseItemDto created = exercise("RUN_TEST");
        created.setId(21L);
        created.setTechniqueReviewStatus(ExerciseTechniqueReviewStatus.PENDING);
        when(exerciseItemService.addItem(any())).thenReturn(created);

        ExerciseItemDto result = service.createExercise("catalog@grun.app", request);

        ArgumentCaptor<ExerciseItemDto> captor = ArgumentCaptor.forClass(ExerciseItemDto.class);
        verify(exerciseItemService).addItem(captor.capture());
        assertEquals(21L, result.getId());
        assertNull(captor.getValue().getId());
        assertEquals(ExerciseTechniqueReviewStatus.PENDING, captor.getValue().getTechniqueReviewStatus());
        assertEquals(false, captor.getValue().getAiEligible());
        verify(adminAuditService).record(
                eq("catalog@grun.app"), eq(AdminAuditActionType.CATALOG_EXERCISE_CREATE),
                eq(AdminAuditTargetType.EXERCISE_ITEM), eq("21"),
                eq(null), any(), any()
        );
    }

    @Test
    void reviewExerciseStoresDecisionAndAudit() {
        ExerciseItemEntity entity = new ExerciseItemEntity();
        entity.setId(7L);
        entity.setTechniqueReviewStatus(ExerciseTechniqueReviewStatus.PENDING);
        ExerciseItemDto mapped = exercise("SQUAT_TEST");
        mapped.setId(7L);
        mapped.setTechniqueReviewStatus(ExerciseTechniqueReviewStatus.APPROVED);
        when(exerciseItemRepository.findById(7L)).thenReturn(Optional.of(entity));
        when(exerciseItemRepository.save(entity)).thenReturn(entity);
        when(exerciseItemMapper.toDto(entity)).thenReturn(mapped);

        service.reviewExercise(
                "reviewer@grun.app", 7L,
                new ExerciseTechniqueReviewRequestDto(
                        ExerciseTechniqueReviewStatus.APPROVED,
                        " Technique and safety evidence checked. "
                )
        );

        assertEquals(ExerciseTechniqueReviewStatus.APPROVED, entity.getTechniqueReviewStatus());
        assertEquals("Technique and safety evidence checked.", entity.getTechniqueReviewNote());
        assertEquals("reviewer@grun.app", entity.getTechniqueReviewedBy());
        verify(adminAuditService).record(
                eq("reviewer@grun.app"), eq(AdminAuditActionType.CATALOG_EXERCISE_REVIEW),
                eq(AdminAuditTargetType.EXERCISE_ITEM), eq("7"),
                any(), any(), any()
        );
    }

    @Test
    void assigningExerciseClaimsItAndMovesPendingItemToReview() {
        ExerciseItemEntity entity = new ExerciseItemEntity();
        entity.setId(8L);
        entity.setTechniqueReviewStatus(ExerciseTechniqueReviewStatus.PENDING);
        when(exerciseItemRepository.findById(8L)).thenReturn(Optional.of(entity));
        LocalDateTime dueAt = LocalDateTime.now().plusDays(2);

        service.assignReview(
                "lead@grun.app", "exercise", 8L,
                new CatalogReviewAssignmentRequestDto(
                        " Reviewer@Grun.App ", dueAt, "Technique queue ownership"
                )
        );

        assertEquals("reviewer@grun.app", entity.getReviewAssignee());
        assertEquals(dueAt, entity.getReviewDueAt());
        assertEquals(ExerciseTechniqueReviewStatus.IN_REVIEW, entity.getTechniqueReviewStatus());
        assertTrue(entity.getReviewClaimedAt() != null);
        verify(exerciseItemRepository).save(entity);
        verify(adminAuditService).record(
                eq("lead@grun.app"), eq(AdminAuditActionType.CATALOG_REVIEW_ASSIGNMENT),
                eq(AdminAuditTargetType.CATALOG_REVIEW_ITEM), eq("EXERCISE:8"),
                any(), any(), any()
        );
    }

    @Test
    void assignmentRejectsExpiredSlaBeforeLoadingCatalogItem() {
        CatalogReviewAssignmentRequestDto request = new CatalogReviewAssignmentRequestDto(
                "reviewer@grun.app", LocalDateTime.now().minusMinutes(1), "Expired deadline"
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.assignReview("lead@grun.app", "EXERCISE", 8L, request)
        );

        assertEquals("Review dueAt must be in the future.", error.getMessage());
        verify(exerciseItemRepository, never()).findById(any());
    }

    private ExerciseItemDto exercise(String metCode) {
        ExerciseItemDto item = new ExerciseItemDto();
        item.setName("Test exercise");
        item.setMetCode(metCode);
        item.setCaloriesPerMinute(7.5);
        item.setActive(true);
        item.setAiEligible(false);
        return item;
    }
}
