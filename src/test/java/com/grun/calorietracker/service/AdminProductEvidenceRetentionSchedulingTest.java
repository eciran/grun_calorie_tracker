package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    void adminApprovalSchedulesPrivateEvidenceDeletion() {
        FoodProductReviewCaseRepository cases = mock(FoodProductReviewCaseRepository.class);
        UserRepository users = mock(UserRepository.class);
        FoodProductReviewCaseAssetRepository assets = mock(FoodProductReviewCaseAssetRepository.class);
        FoodProductReviewCaseService reviewCases = mock(FoodProductReviewCaseService.class);
        FoodProductReviewCaseEvidenceService evidence = mock(FoodProductReviewCaseEvidenceService.class);
        FoodProductEvidenceExpiryScheduler expiry = mock(FoodProductEvidenceExpiryScheduler.class);
        AdminProductIntakeServiceImpl service = new AdminProductIntakeServiceImpl(
                cases,
                users,
                mock(FoodItemRepository.class),
                mock(NotificationRepository.class),
                assets,
                reviewCases,
                new ObjectMapper(),
                24
        );
        service.setEvidenceService(evidence);
        service.setEvidenceExpiryScheduler(expiry);

        UserEntity admin = new UserEntity();
        admin.setEmail("catalog@grun.app");
        admin.setRole(UserRole.ADMIN_CATALOG);
        admin.setAccountEnabled(true);
        FoodProductReviewCaseEntity review = new FoodProductReviewCaseEntity();
        review.setId(42L);
        review.setStatus(FoodProductReviewCaseStatus.SUBMITTED);
        review.setAssignedAdminEmail(admin.getEmail());
        when(users.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(cases.findByIdForAssignment(42L)).thenReturn(Optional.of(review));
        when(cases.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.decideEvidence(42L, admin.getEmail(), true, "Verified label");

        verify(expiry).schedule(42L, FoodProductReviewCaseStatus.APPROVED, review.getReviewedAt());
    }
}
