package com.grun.calorietracker.service;

import com.grun.calorietracker.enums.ProductAnalyticsEventType;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.OnboardingDraftRepository;
import com.grun.calorietracker.repository.ProductAnalyticsEventRepository;
import com.grun.calorietracker.repository.SubscriptionPlanCountProjection;
import com.grun.calorietracker.repository.SubscriptionRepository;
import com.grun.calorietracker.repository.UserActivityDailyCountProjection;
import com.grun.calorietracker.repository.UserDailyActivityRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.AdminGrowthAnalyticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminGrowthAnalyticsServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserDailyActivityRepository activityRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private OnboardingDraftRepository onboardingDraftRepository;
    @Mock private FoodLogsRepository foodLogsRepository;
    @Mock private ProductAnalyticsEventRepository productAnalyticsEventRepository;

    private AdminGrowthAnalyticsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminGrowthAnalyticsServiceImpl(
                userRepository,
                activityRepository,
                subscriptionRepository,
                onboardingDraftRepository,
                foodLogsRepository,
                productAnalyticsEventRepository,
                Clock.fixed(Instant.parse("2026-07-26T12:00:00Z"), ZoneOffset.UTC)
        );
        ReflectionTestUtils.setField(service, "configuredTimeZone", "Europe/Dublin");
    }

    @Test
    void getGrowth_returnsComparisonReadyKpisAndCohortBreakdowns() {
        LocalDate from = LocalDate.of(2026, 7, 20);
        LocalDate to = LocalDate.of(2026, 7, 26);
        Instant currentFrom = Instant.parse("2026-07-19T23:00:00Z");
        Instant currentTo = Instant.parse("2026-07-26T23:00:00Z");
        Instant previousFrom = Instant.parse("2026-07-12T23:00:00Z");
        Instant previousTo = currentFrom;

        when(userRepository.count()).thenReturn(100L);
        when(userRepository.countByCreatedAtIsNull()).thenReturn(10L);
        when(userRepository.countKnownInactiveUsers(currentTo, Instant.parse("2026-06-26T23:00:00Z"))).thenReturn(12L);
        when(userRepository.findRegistrationTimestamps(currentFrom, currentTo)).thenReturn(List.of(
                Instant.parse("2026-07-20T10:00:00Z"),
                Instant.parse("2026-07-25T10:00:00Z")
        ));
        when(userRepository.findRegistrationTimestamps(previousFrom, previousTo))
                .thenReturn(List.of(Instant.parse("2026-07-15T10:00:00Z")));
        when(userRepository.countVerifiedRegistrations(currentFrom, currentTo, currentTo)).thenReturn(1L);
        when(userRepository.countVerifiedRegistrations(previousFrom, previousTo, previousTo)).thenReturn(1L);
        when(userRepository.countVerifiedRegistrationsWithoutTimestamp(currentFrom, currentTo)).thenReturn(1L);
        when(userRepository.countVerifiedRegistrationsWithoutTimestamp(previousFrom, previousTo)).thenReturn(0L);

        when(onboardingDraftRepository.countCompletedForRegistrationCohort(
                currentFrom, currentTo, LocalDateTime.of(2026, 7, 27, 0, 0))).thenReturn(1L);
        when(onboardingDraftRepository.countCompletedForRegistrationCohort(
                previousFrom, previousTo, LocalDateTime.of(2026, 7, 20, 0, 0))).thenReturn(1L);
        when(foodLogsRepository.countUsersWithLogForRegistrationCohort(
                currentFrom, currentTo, LocalDateTime.of(2026, 7, 27, 0, 0))).thenReturn(1L);
        when(foodLogsRepository.countUsersWithLogForRegistrationCohort(
                previousFrom, previousTo, LocalDateTime.of(2026, 7, 20, 0, 0))).thenReturn(1L);
        when(productAnalyticsEventRepository.countUsersForRegistrationCohort(
                ProductAnalyticsEventType.PAYWALL_VIEWED, currentFrom, currentTo, LocalDateTime.of(2026, 7, 27, 0, 0)))
                .thenReturn(1L);
        when(productAnalyticsEventRepository.countUsersForRegistrationCohort(
                ProductAnalyticsEventType.SUBSCRIPTION_STARTED, currentFrom, currentTo, LocalDateTime.of(2026, 7, 27, 0, 0)))
                .thenReturn(1L);
        when(subscriptionRepository.countPaidUsersForRegistrationCohort(
                currentFrom,
                currentTo,
                LocalDate.of(2026, 7, 27)
        )).thenReturn(1L);
        when(subscriptionRepository.countPaidUsersForRegistrationCohort(
                previousFrom,
                previousTo,
                LocalDate.of(2026, 7, 20)
        )).thenReturn(0L);

        when(activityRepository.countByActivityDate(to)).thenReturn(8L);
        when(activityRepository.countByActivityDate(LocalDate.of(2026, 7, 19))).thenReturn(4L);
        when(activityRepository.countDistinctUsersBetween(LocalDate.of(2026, 7, 20), to)).thenReturn(20L);
        when(activityRepository.countDistinctUsersBetween(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19))).thenReturn(10L);
        when(activityRepository.countDistinctUsersBetween(LocalDate.of(2026, 6, 27), to)).thenReturn(40L);
        when(activityRepository.countDistinctUsersBetween(LocalDate.of(2026, 6, 20), LocalDate.of(2026, 7, 19))).thenReturn(30L);
        when(activityRepository.countDailyActiveUsers(from, to)).thenReturn(List.of(activity(to, 8)));

        when(subscriptionRepository.countCurrentUsersByPlanForRegistrationCohort(currentFrom, currentTo))
                .thenReturn(List.of(plan(SubscriptionPlan.PLUS, 1)));
        when(userRepository.countRegistrationsByRegion(currentFrom, currentTo))
                .thenReturn(List.<Object[]>of(new Object[]{"UK_IE", 2L}));
        when(userRepository.countRegistrationsByLanguage(currentFrom, currentTo))
                .thenReturn(List.<Object[]>of(new Object[]{"EN", 2L}));

        var result = service.getGrowth(from, to, "Europe/Dublin");

        assertThat(result.rangeDays()).isEqualTo(7);
        assertThat(result.registrationCoveragePercent()).isEqualTo(90.0);
        assertThat(result.daily()).hasSize(7);
        assertThat(result.daily().stream().mapToLong(point -> point.registrations()).sum()).isEqualTo(2);
        assertThat(result.kpis()).filteredOn(kpi -> kpi.key().equals("NEW_USERS")).singleElement()
                .satisfies(kpi -> {
                    assertThat(kpi.value()).isEqualTo(2.0);
                    assertThat(kpi.previousValue()).isEqualTo(1.0);
                    assertThat(kpi.changePercent()).isEqualTo(100.0);
                });
        assertThat(result.kpis()).filteredOn(kpi -> kpi.key().equals("VERIFIED_RATE")).singleElement()
                .satisfies(kpi -> assertThat(kpi.dataStatus()).isEqualTo("PARTIAL"));
        assertThat(result.funnel()).extracting(step -> step.key())
                .containsExactly("REGISTERED", "VERIFIED", "ONBOARDING", "FIRST_LOG", "PAYWALL", "SUBSCRIBED");
        assertThat(result.planDistribution()).containsEntry("FREE", 1L).containsEntry("PLUS", 1L);
        assertThat(result.regionDistribution()).containsEntry("UK_IE", 2L);
        assertThat(result.languageDistribution()).containsEntry("EN", 2L);
    }

    @Test
    void getGrowth_rejectsRangesLongerThanNinetyDays() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getGrowth(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 7, 26),
                        "Europe/Dublin"
                )
        );
    }

    private UserActivityDailyCountProjection activity(LocalDate date, long users) {
        return new UserActivityDailyCountProjection() {
            public LocalDate getActivityDate() { return date; }
            public long getActiveUsers() { return users; }
        };
    }

    private SubscriptionPlanCountProjection plan(SubscriptionPlan plan, long count) {
        return new SubscriptionPlanCountProjection() {
            public SubscriptionPlan getPlanType() { return plan; }
            public long getSubscriptionCount() { return count; }
        };
    }
}
