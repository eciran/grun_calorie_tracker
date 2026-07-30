package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.FoodProductReviewCaseRepository;
import com.grun.calorietracker.repository.FoodProductReviewCaseAssetRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.service.impl.AdminProductIntakeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminProductIntakeAssignmentServiceTest {
    private final FoodProductReviewCaseRepository repository = mock(FoodProductReviewCaseRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final FoodItemRepository foodItemRepository = mock(FoodItemRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final FoodProductReviewCaseAssetRepository assetRepository = mock(FoodProductReviewCaseAssetRepository.class);
    private final FoodProductReviewCaseService reviewCaseService = mock(FoodProductReviewCaseService.class);
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    private final AdminProductIntakeServiceImpl service = new AdminProductIntakeServiceImpl(repository, users, foodItemRepository, notificationRepository, assetRepository, reviewCaseService, objectMapper, 24);
    private FoodProductReviewCaseEntity reviewCase;

    @BeforeEach
    void setup() {
        reviewCase = new FoodProductReviewCaseEntity();
        reviewCase.setId(44L);
        when(repository.findByIdForAssignment(44L)).thenReturn(Optional.of(reviewCase));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void activeCatalogAdminCanClaimAndTimestampIsSet() {
        when(users.findByEmail("catalog@grun.app")).thenReturn(Optional.of(admin("catalog@grun.app", UserRole.ADMIN_CATALOG, true)));
        var result = service.claim(44L, "catalog@grun.app");
        assertEquals("catalog@grun.app", result.assignedAdminEmail());
        assertNotNull(result.reviewClaimedAt());
    }

    @Test
    void currentCatalogAdminCanReleaseAndTimestampIsCleared() {
        reviewCase.setAssignedAdminEmail("catalog@grun.app");
        reviewCase.setReviewClaimedAt(java.time.LocalDateTime.now());
        when(users.findByEmail("catalog@grun.app")).thenReturn(Optional.of(admin("catalog@grun.app", UserRole.ADMIN_CATALOG, true)));
        var result = service.release(44L, "catalog@grun.app");
        assertNull(result.assignedAdminEmail());
        assertNull(result.reviewClaimedAt());
    }

    @Test
    void ownerCanReassignOnlyToActiveCatalogAdmin() {
        when(users.findByEmail("owner@grun.app")).thenReturn(Optional.of(admin("owner@grun.app", UserRole.OWNER, true)));
        when(users.findByEmail("target@grun.app")).thenReturn(Optional.of(admin("target@grun.app", UserRole.ADMIN_CATALOG, true)));
        var result = service.reassign(44L, "owner@grun.app", "target@grun.app");
        assertEquals("target@grun.app", result.assignedAdminEmail());
        assertNotNull(result.reviewClaimedAt());
    }

    @Test
    void disabledCatalogAdminCannotClaim() {
        when(users.findByEmail("disabled@grun.app")).thenReturn(Optional.of(admin("disabled@grun.app", UserRole.ADMIN_CATALOG, false)));
        assertThrows(AccessDeniedException.class, () -> service.claim(44L, "disabled@grun.app"));
        verify(repository, never()).save(any());
    }

    @Test
    void readOnlyAdminCannotClaimOrReassign() {
        when(users.findByEmail("reader@grun.app")).thenReturn(Optional.of(admin("reader@grun.app", UserRole.ADMIN_READ_ONLY, true)));
        assertThrows(AccessDeniedException.class, () -> service.claim(44L, "reader@grun.app"));
        assertThrows(AccessDeniedException.class, () -> service.reassign(44L, "reader@grun.app", "target@grun.app"));
    }

    private UserEntity admin(String email, UserRole role, boolean enabled) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setRole(role);
        user.setAccountEnabled(enabled);
        return user;
    }
}