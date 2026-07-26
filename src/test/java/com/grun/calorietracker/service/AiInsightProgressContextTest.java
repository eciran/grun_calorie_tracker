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

    private AiInsightServiceImpl service;

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
                configurationValidator);
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
        when(subscriptionService.consumeAiQuota("user@grun.app", 1)).thenReturn(quota);
        when(historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
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
    }

    private DailySummaryDto dailySummary() {
        DailySummaryDto summary = new DailySummaryDto();
        summary.setConsumedCalories(1900.0);
        summary.setBurnedCalories(300.0);
        summary.setTotalExerciseMinutes(45);
        summary.setHasAnyDiaryEntry(true);
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
