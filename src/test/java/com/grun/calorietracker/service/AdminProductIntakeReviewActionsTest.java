package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.impl.AdminProductIntakeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminProductIntakeReviewActionsTest {
    private final FoodProductReviewCaseRepository cases = mock(FoodProductReviewCaseRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final FoodItemRepository foods = mock(FoodItemRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final FoodProductReviewCaseAssetRepository assetRepository = mock(FoodProductReviewCaseAssetRepository.class);
    private final FoodProductReviewCaseService reviewCaseService = mock(FoodProductReviewCaseService.class);
    private final FoodProductReviewCaseEvidenceService evidenceService = mock(FoodProductReviewCaseEvidenceService.class);
    private final CatalogPublicationService publicationService = mock(CatalogPublicationService.class);
    private final com.grun.calorietracker.service.support.ProductIntakeCatalogMutationOrchestrator mutationOrchestrator = mock(com.grun.calorietracker.service.support.ProductIntakeCatalogMutationOrchestrator.class);
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    private final AdminProductIntakeServiceImpl service = new AdminProductIntakeServiceImpl(cases, users, foods, notifications, assetRepository, reviewCaseService, objectMapper, 24);
    private FoodProductReviewCaseEntity reviewCase;

    @BeforeEach
    void setup() {
        service.setEvidenceService(evidenceService);
        service.setCatalogPublicationService(publicationService);
        service.setCatalogMutationOrchestrator(mutationOrchestrator);
        reviewCase = new FoodProductReviewCaseEntity();
        reviewCase.setId(72L);
        reviewCase.setStatus(FoodProductReviewCaseStatus.SUBMITTED);
        reviewCase.setAssignedAdminEmail("catalog@grun.app");
        UserEntity submitter = user("user@grun.app", UserRole.STANDARD, true);
        reviewCase.setSubmittedBy(submitter);
        when(cases.findByIdForAssignment(72L)).thenReturn(Optional.of(reviewCase));
        when(cases.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.findByEmail("catalog@grun.app")).thenReturn(Optional.of(user("catalog@grun.app", UserRole.ADMIN_CATALOG, true)));
    }

    @Test
    void requestEvidenceUpdatesStatusAndCreatesUserVisibleNotification() {
        var result = service.requestBetterEvidence(72L, "catalog@grun.app", "Retake the nutrition label.");
        assertEquals(FoodProductReviewCaseStatus.NEEDS_SUBMITTER_ACTION, result.status());
        verify(notifications).save(argThat(notification -> Boolean.TRUE.equals(notification.getVisibleInApp())
                && notification.getUser() == reviewCase.getSubmittedBy()
                && "FOOD_PRODUCT_REVIEW_CASE".equals(notification.getTargetType())));
    }

    @Test
    void evidenceApprovalDoesNotPublishOrMutateCatalogProduct() {
        FoodItemEntity food = new FoodItemEntity();
        food.setId(8L);
        food.setPublicationStatus(CatalogPublicationStatus.INTERNAL_REVIEW);
        reviewCase.setFoodItem(food);
        var result = service.decideEvidence(72L, "catalog@grun.app", true, "Evidence verified.");
        assertEquals(FoodProductReviewCaseStatus.APPROVED, result.status());
        assertEquals(CatalogPublicationStatus.INTERNAL_REVIEW, food.getPublicationStatus());
        verify(foods, never()).save(any());
        verify(evidenceService).recordAcceptedEvidence(reviewCase);
    }

    @Test
    void rejectEvidenceRecordsFinalReviewerWithoutPublishing() {
        var result = service.decideEvidence(72L, "catalog@grun.app", false, "Barcode is unreadable.");
        assertEquals(FoodProductReviewCaseStatus.REJECTED, result.status());
        assertEquals("catalog@grun.app", reviewCase.getReviewedBy());
        assertNotNull(reviewCase.getReviewedAt());
    }

    @Test
    void attachExistingProductChangesResolutionOnly() {
        FoodItemEntity food = new FoodItemEntity();
        food.setId(99L);
        food.setPublicationStatus(CatalogPublicationStatus.PUBLISHED);
        when(foods.findById(99L)).thenReturn(Optional.of(food));
        var result = service.attachExistingProduct(72L, "catalog@grun.app", 99L);
        assertEquals(99L, result.foodItemId());
        assertEquals(FoodProductResolutionMode.UPDATE_EXISTING, reviewCase.getResolutionMode());
        assertEquals(FoodProductReviewCaseStatus.SUBMITTED, reviewCase.getStatus());
    }

    @Test
    void applyExistingChangesOnlyExplicitlySelectedFields() {
        FoodItemEntity food = new FoodItemEntity();
        food.setId(99L); food.setName("Original"); food.setCalories(100.0); food.setProtein(4.0);
        food.setPublicationStatus(CatalogPublicationStatus.PUBLISHED);
        reviewCase.setFoodItem(food); reviewCase.setResolutionMode(FoodProductResolutionMode.UPDATE_EXISTING);
        reviewCase.setStatus(FoodProductReviewCaseStatus.APPROVED);
        reviewCase.setSubmittedValuesJson("{\"productName\":\"Proposed\",\"calories\":\"220\",\"protein\":\"9\"}");
        var result = service.applyExistingProduct(72L, "catalog@grun.app", java.util.Set.of(ProductIntakeApplyField.CALORIES));
        assertEquals(FoodProductReviewCaseStatus.APPLIED, result.status());
        assertEquals(220.0, food.getCalories()); assertEquals(4.0, food.getProtein()); assertEquals("Original", food.getName());
        assertNotNull(reviewCase.getAppliedAt()); verify(foods).save(food);
        verify(mutationOrchestrator).reconcileAndAudit(eq(food), isNull(), eq("catalog@grun.app"), eq(72L),
                eq(java.util.Map.of("calories", 100.0)), eq(java.util.Map.of("calories", 220.0)));
    }

    @Test
    void applyExistingRejectsInternalCandidateAndLeavesItUnchanged() {
        FoodItemEntity food = new FoodItemEntity(); food.setId(100L); food.setCalories(100.0);
        food.setPublicationStatus(CatalogPublicationStatus.INTERNAL_REVIEW);
        reviewCase.setFoodItem(food); reviewCase.setResolutionMode(FoodProductResolutionMode.NEW_CANDIDATE);
        reviewCase.setStatus(FoodProductReviewCaseStatus.APPROVED); reviewCase.setSubmittedValuesJson("{\"calories\":220}");
        assertThrows(IllegalStateException.class, () -> service.applyExistingProduct(72L, "catalog@grun.app", java.util.Set.of(ProductIntakeApplyField.CALORIES)));
        assertEquals(100.0, food.getCalories()); verify(foods, never()).save(food);
    }

    @Test
    void applyExistingValidatesAllSelectedValuesBeforeMutation() {
        FoodItemEntity food = new FoodItemEntity(); food.setId(101L); food.setCalories(100.0); food.setProtein(4.0);
        food.setPublicationStatus(CatalogPublicationStatus.PUBLISHED);
        reviewCase.setFoodItem(food); reviewCase.setResolutionMode(FoodProductResolutionMode.UPDATE_EXISTING);
        reviewCase.setStatus(FoodProductReviewCaseStatus.APPROVED); reviewCase.setSubmittedValuesJson("{\"calories\":220,\"protein\":\"invalid\"}");
        assertThrows(IllegalArgumentException.class, () -> service.applyExistingProduct(72L, "catalog@grun.app", java.util.Set.of(ProductIntakeApplyField.CALORIES, ProductIntakeApplyField.PROTEIN)));
        assertEquals(100.0, food.getCalories()); assertEquals(4.0, food.getProtein()); verify(foods, never()).save(food);
    }
    @Test
    void approvedNewCandidatePublishesOnlyThroughCentralService() {
        FoodItemEntity food = new FoodItemEntity(); food.setId(202L); food.setName("Candidate"); food.setCalories(120.0);
        food.setVerificationStatus(VerificationStatus.NEEDS_REVIEW); food.setPublicationStatus(CatalogPublicationStatus.INTERNAL_REVIEW);
        reviewCase.setFoodItem(food); reviewCase.setResolutionMode(FoodProductResolutionMode.NEW_CANDIDATE);
        reviewCase.setStatus(FoodProductReviewCaseStatus.APPROVED);
        when(publicationService.publish(202L, "catalog@grun.app", "Verified label", "cid-202")).thenAnswer(invocation -> { food.setPublicationStatus(CatalogPublicationStatus.PUBLISHED); return food; });

        var result = service.publishCandidate(72L, "catalog@grun.app", "Verified label", "cid-202");

        assertEquals(FoodProductReviewCaseStatus.APPLIED, result.status());
        assertEquals(VerificationStatus.VERIFIED, food.getVerificationStatus());
        assertEquals(CatalogPublicationStatus.PUBLISHED, food.getPublicationStatus());
        assertNotNull(reviewCase.getAppliedAt());
        verify(publicationService).publish(202L, "catalog@grun.app", "Verified label", "cid-202");
        verify(mutationOrchestrator).reconcileAndAudit(eq(food), isNull(), eq("catalog@grun.app"), eq(72L), anyMap(), anyMap());
        verify(foods, never()).save(food);
    }

    @Test
    void existingProductCannotUseCandidatePublicationPath() {
        FoodItemEntity food = new FoodItemEntity(); food.setId(203L); food.setPublicationStatus(CatalogPublicationStatus.PUBLISHED);
        reviewCase.setFoodItem(food); reviewCase.setResolutionMode(FoodProductResolutionMode.UPDATE_EXISTING);
        reviewCase.setStatus(FoodProductReviewCaseStatus.APPROVED);
        assertThrows(IllegalStateException.class, () -> service.publishCandidate(72L, "catalog@grun.app", "No", "cid"));
        verifyNoInteractions(publicationService);
    }

    @Test
    void centralPublicationFailureDoesNotMarkCaseApplied() {
        FoodItemEntity food = new FoodItemEntity(); food.setId(204L); food.setPublicationStatus(CatalogPublicationStatus.INTERNAL_REVIEW);
        reviewCase.setFoodItem(food); reviewCase.setResolutionMode(FoodProductResolutionMode.NEW_CANDIDATE);
        reviewCase.setStatus(FoodProductReviewCaseStatus.APPROVED);
        when(publicationService.publish(204L, "catalog@grun.app", "Verified", "cid")).thenThrow(new IllegalArgumentException("Quality gate"));
        assertThrows(IllegalArgumentException.class, () -> service.publishCandidate(72L, "catalog@grun.app", "Verified", "cid"));
        assertEquals(FoodProductReviewCaseStatus.APPROVED, reviewCase.getStatus());
        assertNull(reviewCase.getAppliedAt());
        verify(cases, never()).save(reviewCase);
    }
    @Test
    void nonAssignedCatalogAdminCannotMutateCase() {
        when(users.findByEmail("other@grun.app")).thenReturn(Optional.of(user("other@grun.app", UserRole.ADMIN_CATALOG, true)));
        assertThrows(AccessDeniedException.class,
                () -> service.requestBetterEvidence(72L, "other@grun.app", "Need image."));
        verify(notifications, never()).save(any());
    }

    private UserEntity user(String email, UserRole role, boolean enabled) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setRole(role);
        user.setAccountEnabled(enabled);
        return user;
    }
}