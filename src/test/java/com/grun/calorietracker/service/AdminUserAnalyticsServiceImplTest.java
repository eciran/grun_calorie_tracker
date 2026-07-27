package com.grun.calorietracker.service;

import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.repository.SubscriptionPlanCountProjection;
import com.grun.calorietracker.repository.SubscriptionRepository;
import com.grun.calorietracker.repository.UserActivityDailyCountProjection;
import com.grun.calorietracker.repository.UserDailyActivityRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.AdminUserAnalyticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserAnalyticsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserDailyActivityRepository activityRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    private AdminUserAnalyticsServiceImpl analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AdminUserAnalyticsServiceImpl(
                userRepository,
                activityRepository,
                subscriptionRepository,
                Clock.fixed(Instant.parse("2026-03-30T12:00:00Z"), ZoneOffset.UTC)
        );
        ReflectionTestUtils.setField(analyticsService, "configuredTimeZone", "Europe/Dublin");
    }

    @Test
    void getAnalytics_groupsUtcRegistrationsByDublinDayAndReturnsPrivacySafeCounts() {
        LocalDate from = LocalDate.of(2026, 3, 29);
        LocalDate to = LocalDate.of(2026, 3, 30);
        when(userRepository.findRegistrationTimestamps(
                Instant.parse("2026-03-29T00:00:00Z"),
                Instant.parse("2026-03-30T23:00:00Z")
        )).thenReturn(List.of(
                Instant.parse("2026-03-29T00:30:00Z"),
                Instant.parse("2026-03-29T23:30:00Z")
        ));
        when(activityRepository.countDailyActiveUsers(from, to))
                .thenReturn(List.of(activity(LocalDate.of(2026, 3, 29), 3)));
        when(userRepository.count()).thenReturn(10L);
        when(userRepository.countByCreatedAtIsNull()).thenReturn(4L);
        when(activityRepository.countDistinctUsersBetween(from, to)).thenReturn(5L);
        when(activityRepository.countByActivityDate(to)).thenReturn(2L);
        when(activityRepository.countDistinctUsersBetween(to.minusDays(6), to)).thenReturn(7L);
        when(activityRepository.countDistinctUsersBetween(to.minusDays(29), to)).thenReturn(9L);
        when(subscriptionRepository.countCurrentUsersByPlan())
                .thenReturn(List.of(plan(SubscriptionPlan.PLUS, 2), plan(SubscriptionPlan.PRO, 1)));

        var result = analyticsService.getAnalytics(from, to, "Europe/Dublin");

        assertEquals(2, result.registrationsInRange());
        assertEquals(1, result.daily().get(0).registrations());
        assertEquals(1, result.daily().get(1).registrations());
        assertEquals(3, result.daily().get(0).activeUsers());
        assertEquals(0, result.daily().get(1).activeUsers());
        assertEquals(5, result.activeUsersInRange());
        assertEquals(2, result.dailyActiveUsers());
        assertEquals(7, result.weeklyActiveUsers());
        assertEquals(9, result.monthlyActiveUsers());
        assertEquals(7, result.planDistribution().get(SubscriptionPlan.FREE));
        assertEquals(4, result.legacyUsersWithoutRegistrationDate());
    }

    @Test
    void getAnalytics_rejectsDifferentReportingTimeZone() {
        assertThrows(
                IllegalArgumentException.class,
                () -> analyticsService.getAnalytics(
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 3, 30),
                        "UTC"
                )
        );
    }

    @Test
    void getAnalytics_rejectsRangeLongerThanOneYear() {
        assertThrows(
                IllegalArgumentException.class,
                () -> analyticsService.getAnalytics(
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2026, 3, 30),
                        "Europe/Dublin"
                )
        );
    }

    private UserActivityDailyCountProjection activity(LocalDate date, long activeUsers) {
        return new UserActivityDailyCountProjection() {
            @Override
            public LocalDate getActivityDate() {
                return date;
            }

            @Override
            public long getActiveUsers() {
                return activeUsers;
            }
        };
    }

    private SubscriptionPlanCountProjection plan(SubscriptionPlan plan, long count) {
        return new SubscriptionPlanCountProjection() {
            @Override
            public SubscriptionPlan getPlanType() {
                return plan;
            }

            @Override
            public long getSubscriptionCount() {
                return count;
            }
        };
    }
}
