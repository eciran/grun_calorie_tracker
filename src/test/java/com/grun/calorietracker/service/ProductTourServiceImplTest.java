package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserProductTourEntity;
import com.grun.calorietracker.enums.ProductTourStatus;
import com.grun.calorietracker.repository.UserProductTourRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.ProductTourServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductTourServiceImplTest {

    private UserRepository userRepository;
    private UserProductTourRepository productTourRepository;
    private ProductTourServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        productTourRepository = mock(UserProductTourRepository.class);
        service = new ProductTourServiceImpl(userRepository, productTourRepository);
        user = new UserEntity();
        user.setId(42L);
        user.setEmail("tour@grun.app");
        when(userRepository.findByEmail("tour@grun.app")).thenReturn(Optional.of(user));
    }

    @Test
    void get_withoutDecision_returnsNotStartedWithoutWriting() {
        when(productTourRepository.findByUserAndTourKeyAndTourVersion(
                user, "dashboard", "dashboard-tour-v1")).thenReturn(Optional.empty());

        var result = service.get("tour@grun.app", "dashboard", "dashboard-tour-v1");

        assertEquals(ProductTourStatus.NOT_STARTED, result.getStatus());
        assertNull(result.getCompletedAt());
        verify(productTourRepository, never()).save(any());
    }

    @Test
    void recordDecision_persistsSkipAsFinalState() {
        when(productTourRepository.findByUserAndTourKeyAndTourVersion(
                user, "dashboard", "dashboard-tour-v1")).thenReturn(Optional.empty());
        when(productTourRepository.save(any(UserProductTourEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.recordDecision(
                "tour@grun.app", "dashboard", "dashboard-tour-v1", ProductTourStatus.SKIPPED);

        assertEquals(ProductTourStatus.SKIPPED, result.getStatus());
        assertNotNull(result.getCompletedAt());
        verify(productTourRepository).save(argThat(entity ->
                entity.getUser() == user
                        && entity.getStatus() == ProductTourStatus.SKIPPED));
    }

    @Test
    void recordDecision_rejectsNotStartedAndUnknownContract() {
        assertThrows(IllegalArgumentException.class, () -> service.recordDecision(
                "tour@grun.app", "dashboard", "dashboard-tour-v1", ProductTourStatus.NOT_STARTED));
        assertThrows(IllegalArgumentException.class, () -> service.get(
                "tour@grun.app", "unknown", "dashboard-tour-v1"));
    }
}
