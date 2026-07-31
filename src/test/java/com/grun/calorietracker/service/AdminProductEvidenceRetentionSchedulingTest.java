package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodProductReviewCaseStatus;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodProductReviewCaseAssetRepository;
import com.grun.calorietracker.repository.FoodProductReviewCaseRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.AdminProductIntakeServiceImpl;
import com.grun.calorietracker.service.support.FoodProductEvidenceExpiryScheduler;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminProductEvidenceRetentionSchedulingTest {

    @Test
    void adminApprovalPromotesAllowedMediaBeforeSchedulingPrivateEvidenceDeletion() {
        FoodProductReviewCaseRepository cases = mock(FoodProductReviewCaseRepository.class);
        UserRepository users = mock(UserRepository.class);
        FoodProductReviewCaseAssetRepository assets = mock(FoodProductReviewCaseAssetRepository.class);
        FoodProductReviewCaseService reviewCases = mock(FoodProductReviewCaseService.class);
        FoodProductReviewCaseEvidenceService evidence = mock(FoodProductReviewCaseEvidenceService.class);
        FoodProductEvidenceExpiryScheduler expiry = mock(FoodProductEvidenceExpiryScheduler.class);
        CatalogMediaService catalogMedia = mock(CatalogMediaService.class);
        AdminProductIntakeServiceImpl service = new AdminProductIntakeServiceImpl(
                cases, users, mock(FoodItemRepository.class), mock(NotificationRepository.class),
                assets, reviewCases, new ObjectMapper(), 24);
        service.setEvidenceService(evidence);
        service.setEvidenceExpiryScheduler(expiry);
        service.setCatalogMediaService(catalogMedia);

        UserEntity admin = new UserEntity();
        admin.setEmail("catalog@grun.app");
        admin.setRole(UserRole.ADMIN_CATALOG);
        admin.setAccountEnabled(true);
        FoodItemEntity product = new FoodItemEntity();
        product.setId(77L);
        FoodProductReviewCaseEntity review = new FoodProductReviewCaseEntity();
        review.setId(42L);
        review.setStatus(FoodProductReviewCaseStatus.SUBMITTED);
        review.setAssignedAdminEmail(admin.getEmail());
        review.setFoodItem(product);
        review.setPublicMediaAllowed(true);
        when(users.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(cases.findByIdForAssignment(42L)).thenReturn(Optional.of(review));
        when(cases.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.decideEvidence(42L, admin.getEmail(), true, "Verified label");

        verify(evidence).recordAcceptedEvidence(review);
        verify(catalogMedia).promoteApprovedFrontImage(review, product);
        verify(expiry).schedule(42L, FoodProductReviewCaseStatus.APPROVED, review.getReviewedAt());
    }
}
