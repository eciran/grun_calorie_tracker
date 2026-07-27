package com.grun.calorietracker.service;

import com.grun.calorietracker.config.EnergyBalanceAnalyticsProperties;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.service.support.EnergyBalancePolicy;
import com.grun.calorietracker.service.support.EnergyBalanceRequestGuard;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnergyBalanceRequestGuardTest {

    @Mock private SubscriptionService subscriptionService;
    @Mock private UserService userService;

    private EnergyBalanceRequestGuard guard;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        guard = new EnergyBalanceRequestGuard(
                subscriptionService,
                userService,
                new UserTimeZoneSupport(),
                new EnergyBalancePolicy(new EnergyBalanceAnalyticsProperties())
        );
        user = new UserEntity();
        user.setId(42L);
        user.setEmail("pro@grun.app");
        user.setTimeZone("Europe/Dublin");
    }

    @Test
    void validate_checksResolvedAdvancedAccessAndReturnsUserLocalContext() {
        when(userService.findByEmail("pro@grun.app")).thenReturn(Optional.of(user));

        var context = guard.validate(
                "pro@grun.app",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 27)
        );

        verify(subscriptionService).assertFeatureAccess(
                "pro@grun.app", SubscriptionFeature.ADVANCED_ANALYTICS);
        assertEquals(42L, context.user().getId());
        assertEquals(27, context.dayCount());
        assertEquals("Europe/Dublin", context.zoneId().getId());
    }

    @Test
    void validate_whenAccessDenied_doesNotLoadUserOrStartCalculation() {
        doThrow(new IllegalArgumentException("Subscription does not allow access"))
                .when(subscriptionService)
                .assertFeatureAccess("free@grun.app", SubscriptionFeature.ADVANCED_ANALYTICS);

        assertThrows(IllegalArgumentException.class, () -> guard.validate(
                "free@grun.app",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 27)
        ));

        verify(userService, never()).findByEmail("free@grun.app");
    }
}
