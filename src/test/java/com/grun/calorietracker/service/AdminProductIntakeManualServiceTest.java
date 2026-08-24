package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.AdminProductIntakeManualRequestDto;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.impl.AdminProductIntakeServiceImpl;
import com.grun.calorietracker.service.model.FoodProductReviewCaseCommand;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminProductIntakeManualServiceTest {
    private final FoodProductReviewCaseRepository cases = mock(FoodProductReviewCaseRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final FoodItemRepository foods = mock(FoodItemRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final FoodProductReviewCaseAssetRepository assetRepository = mock(FoodProductReviewCaseAssetRepository.class);
    private final FoodProductReviewCaseService reviewCases = mock(FoodProductReviewCaseService.class);
    private final AdminProductIntakeServiceImpl service = new AdminProductIntakeServiceImpl(
            cases, users, foods, notifications, assetRepository, reviewCases, new ObjectMapper(), 24);

    @Test
    void catalogAdminCreatesAdminManualInternalReviewCandidate() {
        when(users.findByEmail("catalog@grun.app")).thenReturn(Optional.of(admin(UserRole.ADMIN_CATALOG)));
        FoodItemEntity candidate = new FoodItemEntity();
        candidate.setId(301L);
        candidate.setPublicationStatus(CatalogPublicationStatus.INTERNAL_REVIEW);
        FoodProductReviewCaseEntity reviewCase = new FoodProductReviewCaseEntity();
        reviewCase.setId(88L);
        reviewCase.setStatus(FoodProductReviewCaseStatus.SUBMITTED);
        reviewCase.setFoodItem(candidate);
        when(reviewCases.finalizeCase(any())).thenReturn(reviewCase);

        var result = service.createManual("catalog@grun.app", request());

        assertEquals(301L, result.foodItemId());
        verify(reviewCases).finalizeCase(argThat(command -> command.source() == FoodProductReviewCaseSource.ADMIN_MANUAL
                && command.marketRegion() == MarketRegion.EU
                && command.submittedBy() == null
                && !command.temporaryEvidenceAllowed()
                && !command.publicMediaAllowed()));
    }

    @Test
    void readOnlyAdminCannotCreateManualIntake() {
        when(users.findByEmail("reader@grun.app")).thenReturn(Optional.of(admin(UserRole.ADMIN_READ_ONLY)));
        assertThrows(AccessDeniedException.class, () -> service.createManual("reader@grun.app", request()));
        verify(reviewCases, never()).finalizeCase(any(FoodProductReviewCaseCommand.class));
    }

    @Test
    void serviceRejectsAnyCandidateThatIsAlreadyPublished() {
        when(users.findByEmail("catalog@grun.app")).thenReturn(Optional.of(admin(UserRole.ADMIN_CATALOG)));
        FoodItemEntity published = new FoodItemEntity();
        published.setId(302L);
        published.setPublicationStatus(CatalogPublicationStatus.PUBLISHED);
        FoodProductReviewCaseEntity reviewCase = new FoodProductReviewCaseEntity();
        reviewCase.setFoodItem(published);
        when(reviewCases.finalizeCase(any())).thenReturn(reviewCase);
        assertThrows(IllegalStateException.class, () -> service.createManual("catalog@grun.app", request()));
    }

    private AdminProductIntakeManualRequestDto request() {
        return new AdminProductIntakeManualRequestDto("admin-manual-1", "3017620422003", MarketRegion.EU,
                "Hazelnut spread", "Example", 539.0, 6.3, 30.9, 57.5, 3.4, 56.3, 0.1,
                FoodNutritionBasis.SOURCE_REPORTED);
    }

    private UserEntity admin(UserRole role) {
        UserEntity user = new UserEntity();
        user.setEmail(role == UserRole.ADMIN_READ_ONLY ? "reader@grun.app" : "catalog@grun.app");
        user.setRole(role);
        user.setAccountEnabled(true);
        return user;
    }
}