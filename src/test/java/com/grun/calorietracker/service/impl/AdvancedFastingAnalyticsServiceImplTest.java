package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdvancedFastingAnalyticsDto;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.FastingProgramOccurrenceRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdvancedFastingAnalyticsServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private FastingProgramOccurrenceRepository occurrenceRepository;
    @Mock private SubscriptionService subscriptionService;
    private AdvancedFastingAnalyticsServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        service = new AdvancedFastingAnalyticsServiceImpl(userRepository, occurrenceRepository, subscriptionService);
        user = new UserEntity();
        user.setId(7L);
        user.setEmail("user@grun.test");
    }

    @Test
    void requiresBothAdvancedFastingAndAdvancedAnalyticsEntitlements() {
        doNothing().when(subscriptionService)
                .assertFeatureAccess(user.getEmail(), SubscriptionFeature.FASTING_ADVANCED);
        doThrow(new IllegalStateException("denied"))
                .when(subscriptionService)
                .assertFeatureAccess(user.getEmail(), SubscriptionFeature.ADVANCED_ANALYTICS);

        assertThatThrownBy(() -> service.getAnalytics(
                user.getEmail(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("denied");

        verify(subscriptionService).assertFeatureAccess(user.getEmail(), SubscriptionFeature.FASTING_ADVANCED);
        verify(subscriptionService).assertFeatureAccess(user.getEmail(), SubscriptionFeature.ADVANCED_ANALYTICS);
        verifyNoInteractions(userRepository, occurrenceRepository);
    }

    @Test
    void keepsUnavailableMetricsNullForPartialData() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(occurrenceRepository.findByUserAndOccurrenceDateBetweenOrderByOccurrenceDateAsc(user, start, start))
                .thenReturn(List.of(occurrence(start, FastingDayRuleType.REDUCED_CALORIE,
                        FastingAdherenceStatus.UNKNOWN, null)));

        AdvancedFastingAnalyticsDto result = service.getAnalytics(user.getEmail(), start, start);

        assertThat(result.adherencePercent()).isNull();
        assertThat(result.scheduleConsistencyPercent()).isNull();
        assertThat(result.averageDurationMinutes()).isNull();
        assertThat(result.earlyStopCount()).isNull();
        assertThat(result.fiveTwoAdherencePercent()).isNull();
        assertThat(result.minimumDataMet()).isFalse();
        assertThat(result.dataQualityIndicators()).contains(
                "LIMITED_EVALUABLE_DATA", "NO_SCHEDULE_TIMING_DATA",
                "NO_COMPLETED_DURATION_DATA", "UNKNOWN_ADHERENCE_PRESENT");
    }

    @Test
    void rejectsRangesLongerThan366DaysBeforeAccessOrDatabaseCalls() {
        assertThatThrownBy(() -> service.getAnalytics(
                user.getEmail(), LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("366");

        verifyNoInteractions(subscriptionService, userRepository, occurrenceRepository);
    }

    @Test
    void calculatesAnalyticsWithOneBoundedOccurrenceQuery() {
        LocalDate start = LocalDate.of(2026, 7, 6);
        FastingSessionEntity metSession = session(start, 960, 960, FastingSessionOutcomeReason.TARGET_COMPLETED);
        FastingSessionEntity earlySession = session(start.plusDays(1), 900, 600, FastingSessionOutcomeReason.USER_ENDED);
        List<FastingProgramOccurrenceEntity> occurrences = List.of(
                occurrence(start, FastingDayRuleType.FAST, FastingAdherenceStatus.MET, metSession),
                occurrence(start.plusDays(1), FastingDayRuleType.FAST, FastingAdherenceStatus.NOT_MET, earlySession),
                occurrence(start.plusDays(2), FastingDayRuleType.REDUCED_CALORIE, FastingAdherenceStatus.MET, null));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(occurrenceRepository.findByUserAndOccurrenceDateBetweenOrderByOccurrenceDateAsc(
                user, start, start.plusDays(6))).thenReturn(occurrences);

        AdvancedFastingAnalyticsDto result = service.getAnalytics(user.getEmail(), start, start.plusDays(6));

        assertThat(result.adherencePercent()).isEqualTo(66.7);
        assertThat(result.scheduleConsistencyPercent()).isEqualTo(50.0);
        assertThat(result.averageStartDeviationMinutes()).isEqualTo(30.0);
        assertThat(result.averageDurationMinutes()).isEqualTo(780.0);
        assertThat(result.earlyStopCount()).isEqualTo(1);
        assertThat(result.earlyStopPercent()).isEqualTo(50.0);
        assertThat(result.fiveTwoAdherencePercent()).isEqualTo(100.0);
        assertThat(result.stopReasonDistribution()).containsEntry(FastingSessionOutcomeReason.USER_ENDED, 1);
        verify(occurrenceRepository, times(1))
                .findByUserAndOccurrenceDateBetweenOrderByOccurrenceDateAsc(user, start, start.plusDays(6));
        verifyNoMoreInteractions(occurrenceRepository);
    }

    private FastingProgramOccurrenceEntity occurrence(
            LocalDate date,
            FastingDayRuleType type,
            FastingAdherenceStatus adherence,
            FastingSessionEntity session) {
        FastingProgramOccurrenceEntity occurrence = new FastingProgramOccurrenceEntity();
        occurrence.setOccurrenceDate(date);
        occurrence.setRuleType(type);
        occurrence.setAdherenceStatus(adherence);
        occurrence.setFastingSession(session);
        if (type == FastingDayRuleType.FAST) {
            occurrence.setPlannedStartAt(date.atTime(20, 0));
            occurrence.setPlannedFastingMinutes(900);
        }
        return occurrence;
    }

    private FastingSessionEntity session(
            LocalDate date,
            int plannedMinutes,
            int actualMinutes,
            FastingSessionOutcomeReason reason) {
        FastingSessionEntity session = new FastingSessionEntity();
        session.setStatus(FastingSessionStatus.COMPLETED);
        session.setStartedAt(date.atTime(20, reason == FastingSessionOutcomeReason.USER_ENDED ? 45 : 15));
        session.setPlannedFastingMinutes(plannedMinutes);
        session.setActualMinutes(actualMinutes);
        session.setOutcomeReason(reason);
        return session;
    }
}