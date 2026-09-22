package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiInsightRequestDto;
import com.grun.calorietracker.dto.AiInsightResponseDto;
import com.grun.calorietracker.dto.DailySummaryDto;
import com.grun.calorietracker.dto.ProgressAnalyticsDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AiInsightFocus;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.AiInsightServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiInsightProgressContextTest {

    @Mock private AiMealDraftProviderClient providerClient;
    @Mock private AiRequestHistoryRepository historyRepository;
    @Mock private UserRepository userRepository;
    @Mock private DashboardService dashboardService;
    @Mock private ProgressAnalyticsService progressAnalyticsService;
    @Mock private SubscriptionService subscriptionService;
    @Mock private AiProviderConfigurationValidator configurationValidator;
    @Mock private WaterTrackingService waterTrackingService;
    @Mock private SleepTrackingService sleepTrackingService;

    private AiInsightServiceImpl service;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(AiInsightFocus.class)
    void allDailyFocusesReceiveRecordedSignals(AiInsightFocus focus) {
        stubProvider(false);
        LocalDate date = LocalDate.of(2026, 9, 17);
        DailySummaryDto day = dailySummary();
        day.setSummaryDate(date);
        day.setConsumedProtein(90.0);
        day.setTargetProtein(120.0);
        day.setConsumedCarbs(160.0);
        day.setConsumedFat(55.0);
        day.setCurrentWeight(80.0);
        day.setTargetWeight(75.0);
        var steps = new com.grun.calorietracker.dto.StepDailySummaryDto();
        steps.setHasStepData(true); steps.setTotalSteps(8500); steps.setTargetSteps(10000);
        day.setStepSummary(steps);
        when(dashboardService.getDailySummary("user@grun.app", date)).thenReturn(day);
        var water = new com.grun.calorietracker.dto.WaterDailySummaryDto();
        water.setTotalMl(3750); water.setTargetMl(2500);
        when(waterTrackingService.getDailySummary("user@grun.app", date)).thenReturn(water);
        when(sleepTrackingService.dailySummary("user@grun.app", date)).thenReturn(
                com.grun.calorietracker.dto.SleepDailySummaryDto.builder()
                        .sessionCount(1).totalSleepMinutes(450).targetMinutes(480).build());
        var request = new AiInsightRequestDto(); request.setDate(date); request.setFocus(focus);
        var result = service.createDailyInsight("user@grun.app", request);
        var captor = ArgumentCaptor.forClass(AiInsightRequestDto.class);
        verify(providerClient).createDailyInsight(captor.capture());
        Map<?, ?> context = (Map<?, ?>) captor.getValue().getContext();
        assertEquals(3750, context.get("waterMl"));
        assertEquals(2500, context.get("waterTargetMl"));
        assertEquals(450, context.get("sleepMinutes"));
        assertEquals(8500, context.get("steps"));
        assertEquals(90.0, context.get("consumedProteinGrams"));
        assertEquals(160.0, context.get("consumedCarbsGrams"));
        assertEquals(55.0, context.get("consumedFatGrams"));
        assertEquals(80.0, context.get("currentWeightKg"));
        assertTrue(result.getDataCoverage().getSignalsUsed().containsAll(List.of("water", "sleep", "step count")));
        assertFalse(result.getDataCoverage().getMissingSignals().contains("water"));
        assertEquals(1, result.getDataCoverage().getDaysAnalyzed());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(AiInsightFocus.class)
    void weeklyContextPreservesDailyRecordsAndDoesNotAverageUnloggedDaysAsZero(AiInsightFocus focus) {
        stubProvider(true);
        LocalDate start = LocalDate.of(2026, 9, 16), end = start.plusDays(1);
        DailySummaryDto logged = dailySummary(); logged.setSummaryDate(start);
        DailySummaryDto empty = new DailySummaryDto(); empty.setSummaryDate(end);
        empty.setHasFoodLogs(false); empty.setConsumedCalories(0.0);
        when(dashboardService.getDailySummary("user@grun.app", start)).thenReturn(logged);
        when(dashboardService.getDailySummary("user@grun.app", end)).thenReturn(empty);
        var water = new com.grun.calorietracker.dto.WaterDailySummaryDto(); water.setTotalMl(3750); water.setTargetMl(2500);
        when(waterTrackingService.getDailySummary("user@grun.app", start)).thenReturn(water);
        var request = new AiInsightRequestDto(); request.setStartDate(start); request.setEndDate(end); request.setFocus(focus);
        var result = service.createWeeklyInsight("user@grun.app", request);
        var captor = ArgumentCaptor.forClass(AiInsightRequestDto.class);
        verify(providerClient).createWeeklyInsight(captor.capture());
        Map<?, ?> context = (Map<?, ?>) captor.getValue().getContext();
        assertEquals(1900.0, context.get("averageConsumedCalories"));
        assertEquals(1, context.get("foodLoggedDays"));
        List<?> days = (List<?>) context.get("dailyData");
        assertEquals(2, days.size());
        assertEquals(start, ((Map<?, ?>) days.get(0)).get("date"));
        assertEquals(3750, ((Map<?, ?>) days.get(0)).get("waterMl"));
        assertEquals(false, ((Map<?, ?>) days.get(1)).get("waterDataAvailable"));
        assertTrue(result.getDataCoverage().getSignalsUsed().contains("water"));
        assertFalse(result.getDataCoverage().getMissingSignals().contains("water"));
    }

    private void stubProvider(boolean weekly) {
        when(providerClient.provider()).thenReturn(AiProvider.LOG);
        var response = providerResponse();
        var coverage = new AiInsightResponseDto.DataCoverage();
        coverage.setDaysAnalyzed(999);
        coverage.setMissingSignals(List.of("water", "hydration logs"));
        response.setDataCoverage(coverage);
        if (weekly) when(providerClient.createWeeklyInsight(any())).thenReturn(response);
        else when(providerClient.createDailyInsight(any())).thenReturn(response);
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(new UserEntity()));
        when(subscriptionService.resolveAiCreditCost("user@grun.app", SubscriptionFeature.AI_INSIGHTS)).thenReturn(1);
        SubscriptionDto quota = new SubscriptionDto(); quota.setAiRemainingThisPeriod(8);
        when(subscriptionService.consumeAiRequestQuota(org.mockito.ArgumentMatchers.eq("user@grun.app"), org.mockito.ArgumentMatchers.eq(1), any())).thenReturn(quota);
        when(historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        properties.setProvider(AiProvider.LOG);
        properties.setModel("test-model");
        service = new AiInsightServiceImpl(
                properties,
                List.of(providerClient),
                historyRepository,
                userRepository,
                dashboardService,
                progressAnalyticsService,
                subscriptionService,
                new ObjectMapper().findAndRegisterModules(),
                configurationValidator, waterTrackingService, sleepTrackingService);
    }

    @Test
    void weeklyWeightGoalInsight_UsesBackendProgressAnalyticsContext() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 7);
        UserEntity user = new UserEntity();
        SubscriptionDto quota = new SubscriptionDto();
        quota.setAiRemainingThisPeriod(8);

        when(providerClient.provider()).thenReturn(AiProvider.LOG);
        when(providerClient.createWeeklyInsight(any())).thenReturn(providerResponse());
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(subscriptionService.hasFeatureAccess(
                "user@grun.app", SubscriptionFeature.ADVANCED_ANALYTICS)).thenReturn(true);
        when(subscriptionService.resolveAiCreditCost(
                "user@grun.app", SubscriptionFeature.AI_INSIGHTS)).thenReturn(1);
        when(subscriptionService.consumeAiRequestQuota(org.mockito.ArgumentMatchers.eq("user@grun.app"), org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.any())).thenReturn(quota);
        when(historyRepository.save(any())).thenAnswer(invocation -> {
            com.grun.calorietracker.entity.AiRequestHistoryEntity history = invocation.getArgument(0);
            assertTrue(history.isCoachingCompletionNotificationEligible());
            return history;
        });
        when(dashboardService.getDailySummary(any(), any())).thenReturn(dailySummary());
        when(progressAnalyticsService.getAnalytics("user@grun.app", start, end, false))
                .thenReturn(progressAnalytics());

        AiInsightRequestDto request = new AiInsightRequestDto();
        request.setStartDate(start);
        request.setEndDate(end);
        request.setFocus(AiInsightFocus.WEIGHT_GOAL);

        AiInsightResponseDto response = service.createWeeklyInsight("user@grun.app", request);

        ArgumentCaptor<AiInsightRequestDto> providerRequest = ArgumentCaptor.forClass(AiInsightRequestDto.class);
        verify(providerClient).createWeeklyInsight(providerRequest.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) providerRequest.getValue().getContext();
        assertEquals(88.0, context.get("currentWeightKg"));
        assertEquals(80.0, context.get("targetWeightKg"));
        assertEquals(-0.5, context.get("weeklyWeightChangeKg"));
        assertEquals("PROJECTED", context.get("projectionStatus"));
        assertEquals(true, context.get("progressTrendSufficient"));
        assertTrue(response.getDataCoverage().getSignalsUsed().contains("weight and goal trend"));
        assertEquals(7, context.get("foodLoggedDays"));
        assertTrue(response.getPersonalizedActions().isEmpty(), "Focused coaching must not inject general nutrition actions");
        assertTrue(response.getKeyFindings().isEmpty(), "Focused coaching must not inject unrelated fallback findings");
    }

    private DailySummaryDto dailySummary() {
        DailySummaryDto summary = new DailySummaryDto();
        summary.setConsumedCalories(1900.0);
        summary.setBurnedCalories(300.0);
        summary.setTotalExerciseMinutes(45);
        summary.setHasAnyDiaryEntry(true);
        summary.setHasFoodLogs(true);
        return summary;
    }

    private AiInsightResponseDto providerResponse() {
        AiInsightResponseDto response = new AiInsightResponseDto();
        response.setSummary("Review your measured progress trend.");
        return response;
    }

    private ProgressAnalyticsDto progressAnalytics() {
        return ProgressAnalyticsDto.builder()
                .body(ProgressAnalyticsDto.Body.builder()
                        .currentWeightKg(88.0)
                        .targetWeightKg(80.0)
                        .weeklyChangeKg(-0.5)
                        .goalProgressPercent(50.0)
                        .projectionStatus("PROJECTED")
                        .projectionConfidence("MEDIUM")
                        .projectedGoalDate(LocalDate.of(2026, 11, 18))
                        .weightPoints(List.of(
                                ProgressAnalyticsDto.WeightPoint.builder().date(LocalDate.of(2026, 7, 1)).weightKg(88.5).build(),
                                ProgressAnalyticsDto.WeightPoint.builder().date(LocalDate.of(2026, 7, 7)).weightKg(88.0).build()))
                        .build())
                .weightPlateau(ProgressAnalyticsDto.ProgressSignal.builder().status("NOT_DETECTED").build())
                .dataCoverage(ProgressAnalyticsDto.DataCoverage.builder().sufficientForTrend(true).build())
                .build();
    }
}
