package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodLogDailyStatsDto;
import com.grun.calorietracker.dto.MicronutrientAnalyticsDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.service.impl.DefaultMicronutrientReferenceService;
import com.grun.calorietracker.service.impl.MicronutrientAnalyticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MicronutrientAnalyticsServiceImplTest {

    @Mock private SubscriptionService subscriptionService;
    @Mock private UserService userService;
    @Mock private FoodLogsService foodLogsService;
    @Mock private UserAnalyticsCacheRevisionService analyticsCacheRevisionService;
    @Mock private com.grun.calorietracker.service.support.UserAnalyticsCacheGateway analyticsCacheGateway;
    @Mock private com.grun.calorietracker.service.support.UserAnalyticsCacheKeyFactory analyticsCacheKeyFactory;

    private MicronutrientAnalyticsServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        service = new MicronutrientAnalyticsServiceImpl(
                subscriptionService,
                userService,
                foodLogsService,
                new DefaultMicronutrientReferenceService(),
                analyticsCacheRevisionService,
                analyticsCacheGateway,
                analyticsCacheKeyFactory
        );
        org.mockito.Mockito.lenient().when(analyticsCacheRevisionService.requireIdentity(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new com.grun.calorietracker.service.support.UserAnalyticsCacheIdentity(1L, 0L, "Europe/Dublin"));
        org.mockito.Mockito.lenient().when(analyticsCacheKeyFactory.key(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn("test-key");
        org.mockito.Mockito.lenient().doAnswer(invocation ->
                ((java.util.function.Supplier<?>) invocation.getArgument(2)).get())
                .when(analyticsCacheGateway).get(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.nullable(String.class), org.mockito.ArgumentMatchers.any());
        user = new UserEntity();
        user.setEmail("micro@grun.app");
        user.setAge(32);
        user.setTimeZone("Europe/Dublin");
    }

    @Test
    void getAnalytics_ReturnsNullSafeTrendsCoverageTargetsAndComparison() {
        LocalDate start = LocalDate.of(2026, 7, 2);
        LocalDate end = LocalDate.of(2026, 7, 8);
        LocalDate previousStart = LocalDate.of(2026, 6, 25);
        LocalDate previousEnd = LocalDate.of(2026, 7, 1);
        when(userService.findByEmail("micro@grun.app")).thenReturn(Optional.of(user));
        when(foodLogsService.getDailyStats(
                "micro@grun.app", start.atStartOfDay(), end.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(
                        food("2026-07-02", 1500.0, 800.0),
                        food("2026-07-03", 2500.0, 400.0),
                        food("2026-07-04", 1000.0, 900.0),
                        food("2026-07-05", 3000.0, null),
                        food("2026-07-09", 9000.0, 9000.0)
                ));
        when(foodLogsService.getDailyStats(
                "micro@grun.app", previousStart.atStartOfDay(), previousEnd.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(
                        food("2026-06-25", 1000.0, null),
                        food("2026-06-26", 1000.0, null),
                        food("2026-06-27", 1000.0, null),
                        food("2026-06-28", 1000.0, null)
                ));

        MicronutrientAnalyticsDto result =
                service.getAnalytics("micro@grun.app", start, end, true);

        verify(subscriptionService).assertFeatureAccess(
                "micro@grun.app", SubscriptionFeature.MICRONUTRIENT_ANALYTICS);
        assertEquals(7, result.getRange().getDayCount());
        assertEquals(previousStart, result.getRange().getComparisonStartDate());
        assertEquals(4, result.getCoverage().getFoodLoggedDays());
        assertEquals(11, result.getCoverage().getTrackedNutrientCount());
        assertEquals(57.14, result.getCoverage().getFoodDiaryCoveragePercent());
        assertEquals(15.91, result.getCoverage().getAverageMicronutrientCoveragePercent());
        assertEquals("LOW", result.getSummary().getDataConfidence());
        assertEquals(1, result.getSummary().getEvaluatedNutrientCount());
        assertEquals(1, result.getSummary().getWithinReferenceCount());
        assertEquals(0, result.getSummary().getAttentionNutrientCount());
        assertEquals(1, result.getSummary().getInsufficientDataNutrientCount());
        assertEquals(9, result.getSummary().getNoDataNutrientCount());
        assertEquals(1, result.getInsights().size());
        assertEquals("MICRONUTRIENT_DATA_INCOMPLETE", result.getInsights().get(0).getCode());

        MicronutrientAnalyticsDto.NutrientMetric sodium = nutrient(result, "SODIUM");
        assertEquals(2000.0, sodium.getTarget());
        assertEquals(2000.0, sodium.getAverageOnAvailableDays());
        assertEquals(4, sodium.getAvailableDayCount());
        assertEquals(2, sodium.getTargetHitDays());
        assertEquals(50.0, sodium.getTargetHitRatePercent());
        assertEquals("WITHIN_REFERENCE", sodium.getInterpretation());
        assertEquals(7, sodium.getTrend().size());
        assertNull(sodium.getTrend().get(4).getValue());
        assertNull(sodium.getTrend().get(4).getTargetMet());
        assertTrue(sodium.getComparison().isSufficientData());
        assertEquals(1000.0, sodium.getComparison().getAbsoluteChange());
        assertEquals(100.0, sodium.getComparison().getPercentChange());
        assertEquals("UP", sodium.getComparison().getDirection());

        MicronutrientAnalyticsDto.NutrientMetric calcium = nutrient(result, "CALCIUM");
        assertEquals(3, calcium.getAvailableDayCount());
        assertEquals("INSUFFICIENT_DATA", calcium.getInterpretation());
        assertFalse(calcium.getComparison().isSufficientData());
    }

    @Test
    void getAnalytics_ForMinor_ReturnsTrendsWithoutAdultTargetInterpretation() {
        user.setAge(17);
        LocalDate date = LocalDate.of(2026, 7, 8);
        FoodLogDailyStatsDto day = new FoodLogDailyStatsDto();
        day.setDate(date.toString());
        day.setTotalPotassium(2200.0);
        when(userService.findByEmail("micro@grun.app")).thenReturn(Optional.of(user));
        when(foodLogsService.getDailyStats(
                eq("micro@grun.app"), eq(date.atStartOfDay()), eq(date.plusDays(1).atStartOfDay())))
                .thenReturn(List.of(day));

        MicronutrientAnalyticsDto result =
                service.getAnalytics("micro@grun.app", date, date, false);

        assertFalse(result.getTargetProfile().isApplicable());
        assertEquals("ADULT_PROFILE_NOT_APPLICABLE", result.getTargetProfile().getUnavailableReason());
        MicronutrientAnalyticsDto.NutrientMetric potassium = nutrient(result, "POTASSIUM");
        assertNull(potassium.getTarget());
        assertEquals(2200.0, potassium.getAverageOnAvailableDays());
        assertEquals("TARGET_UNAVAILABLE", potassium.getInterpretation());
        assertNull(potassium.getTargetHitDays());
        assertNull(potassium.getComparison());
    }

    @Test
    void getAnalytics_WithReferenceAttention_ReturnsRankedNonDiagnosticInsights() {
        LocalDate start = LocalDate.of(2026, 7, 5);
        LocalDate end = LocalDate.of(2026, 7, 8);
        when(userService.findByEmail("micro@grun.app")).thenReturn(Optional.of(user));
        when(foodLogsService.getDailyStats(
                "micro@grun.app", start.atStartOfDay(), end.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(
                        food("2026-07-05", 3000.0, 200.0),
                        food("2026-07-06", 3000.0, 200.0),
                        food("2026-07-07", 3000.0, 200.0),
                        food("2026-07-08", 3000.0, 200.0)
                ));

        MicronutrientAnalyticsDto result =
                service.getAnalytics("micro@grun.app", start, end, false);

        assertEquals("LOW", result.getSummary().getDataConfidence());
        assertEquals(2, result.getSummary().getAttentionNutrientCount());
        assertEquals(3, result.getInsights().size());
        assertEquals("CAUTION", result.getInsights().get(0).getTone());
        assertEquals("CALCIUM", result.getInsights().get(0).getNutrientCode());
        assertEquals("MICRONUTRIENT_BELOW_REFERENCE", result.getInsights().get(0).getCode());
        assertEquals("MICRONUTRIENT_DATA_INCOMPLETE", result.getInsights().get(2).getCode());
    }

    @Test
    void getAnalytics_WithCompleteDiary_ReturnsHighConfidencePositiveSummary() {
        LocalDate start = LocalDate.of(2026, 7, 2);
        LocalDate end = LocalDate.of(2026, 7, 8);
        when(userService.findByEmail("micro@grun.app")).thenReturn(Optional.of(user));
        when(foodLogsService.getDailyStats(
                "micro@grun.app", start.atStartOfDay(), end.plusDays(1).atStartOfDay()))
                .thenReturn(start.datesUntil(end.plusDays(1))
                        .map(this::completeFood)
                        .toList());

        MicronutrientAnalyticsDto result =
                service.getAnalytics("micro@grun.app", start, end, false);

        assertEquals("HIGH", result.getSummary().getDataConfidence());
        assertEquals(11, result.getSummary().getEvaluatedNutrientCount());
        assertEquals(11, result.getSummary().getWithinReferenceCount());
        assertEquals(0, result.getSummary().getAttentionNutrientCount());
        assertEquals(1, result.getInsights().size());
        assertEquals("POSITIVE", result.getInsights().get(0).getTone());
    }

    @Test
    void getAnalytics_WhenPreviousBaselineIsTiny_SuppressesMisleadingPercentage() {
        LocalDate start = LocalDate.of(2026, 7, 5);
        LocalDate end = LocalDate.of(2026, 7, 8);
        LocalDate previousStart = LocalDate.of(2026, 7, 1);
        LocalDate previousEnd = LocalDate.of(2026, 7, 4);
        when(userService.findByEmail("micro@grun.app")).thenReturn(Optional.of(user));
        when(foodLogsService.getDailyStats(
                "micro@grun.app", start.atStartOfDay(), end.plusDays(1).atStartOfDay()))
                .thenReturn(start.datesUntil(end.plusDays(1))
                        .map(date -> food(date.toString(), 740.0, null))
                        .toList());
        when(foodLogsService.getDailyStats(
                "micro@grun.app", previousStart.atStartOfDay(), previousEnd.plusDays(1).atStartOfDay()))
                .thenReturn(previousStart.datesUntil(previousEnd.plusDays(1))
                        .map(date -> food(date.toString(), 0.83, null))
                        .toList());

        MicronutrientAnalyticsDto.NutrientMetric sodium = nutrient(
                service.getAnalytics("micro@grun.app", start, end, true),
                "SODIUM"
        );

        assertFalse(sodium.getComparison().isSufficientData());
        assertNull(sodium.getComparison().getPercentChange());
        assertEquals(739.17, sodium.getComparison().getAbsoluteChange());
        assertEquals("INSUFFICIENT_DATA", sodium.getComparison().getDirection());
    }

    @Test
    void getAnalytics_WhenEitherPeriodCoverageIsBelowFortyPercent_SuppressesComparison() {
        LocalDate start = LocalDate.of(2026, 6, 29);
        LocalDate end = LocalDate.of(2026, 7, 28);
        LocalDate previousStart = LocalDate.of(2026, 5, 30);
        LocalDate previousEnd = LocalDate.of(2026, 6, 28);
        when(userService.findByEmail("micro@grun.app")).thenReturn(Optional.of(user));
        when(foodLogsService.getDailyStats(
                "micro@grun.app", start.atStartOfDay(), end.plusDays(1).atStartOfDay()))
                .thenReturn(start.datesUntil(start.plusDays(15))
                        .map(date -> food(date.toString(), 740.0, null))
                        .toList());
        when(foodLogsService.getDailyStats(
                "micro@grun.app", previousStart.atStartOfDay(), previousEnd.plusDays(1).atStartOfDay()))
                .thenReturn(previousStart.datesUntil(previousStart.plusDays(11))
                        .map(date -> food(date.toString(), 600.0, null))
                        .toList());

        MicronutrientAnalyticsDto.NutrientMetric sodium = nutrient(
                service.getAnalytics("micro@grun.app", start, end, true),
                "SODIUM"
        );

        assertFalse(sodium.getComparison().isSufficientData());
        assertNull(sodium.getComparison().getPercentChange());
        assertEquals("INSUFFICIENT_DATA", sodium.getComparison().getDirection());
    }

    @Test
    void getAnalytics_WhenRangeExceedsLimit_RejectsBeforeLoadingData() {
        assertThrows(IllegalArgumentException.class, () -> service.getAnalytics(
                "micro@grun.app",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 2),
                false
        ));
    }

    private MicronutrientAnalyticsDto.NutrientMetric nutrient(
            MicronutrientAnalyticsDto result,
            String code
    ) {
        return result.getNutrients().stream()
                .filter(metric -> code.equals(metric.getCode()))
                .findFirst()
                .orElseThrow();
    }

    private FoodLogDailyStatsDto food(String date, Double sodium, Double calcium) {
        FoodLogDailyStatsDto dto = new FoodLogDailyStatsDto();
        dto.setDate(date);
        dto.setTotalSodium(sodium);
        dto.setTotalCalcium(calcium);
        return dto;
    }

    private FoodLogDailyStatsDto completeFood(LocalDate date) {
        FoodLogDailyStatsDto dto = new FoodLogDailyStatsDto();
        dto.setDate(date.toString());
        dto.setTotalSodium(1800.0);
        dto.setTotalPotassium(3500.0);
        dto.setTotalCalcium(800.0);
        dto.setTotalIron(14.0);
        dto.setTotalMagnesium(375.0);
        dto.setTotalZinc(10.0);
        dto.setTotalVitaminA(800.0);
        dto.setTotalVitaminC(80.0);
        dto.setTotalVitaminD(5.0);
        dto.setTotalVitaminE(12.0);
        dto.setTotalVitaminB12(2.5);
        return dto;
    }


    @Test
    void getAnalytics_checksFeatureAccessOnEveryRequestEvenWhenGatewayReturnsCachedValue() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 7);
        MicronutrientAnalyticsDto cached = org.mockito.Mockito.mock(MicronutrientAnalyticsDto.class);
        org.mockito.Mockito.reset(analyticsCacheGateway);
        org.mockito.Mockito.doReturn(cached).when(analyticsCacheGateway).get(
                eq(com.grun.calorietracker.config.UserAnalyticsCacheNames.MICRONUTRIENTS),
                org.mockito.ArgumentMatchers.nullable(String.class),
                org.mockito.ArgumentMatchers.any()
        );

        assertEquals(cached, service.getAnalytics("micro@grun.app", start, end, false));
        assertEquals(cached, service.getAnalytics("micro@grun.app", start, end, false));

        verify(subscriptionService, times(2)).assertFeatureAccess(
                "micro@grun.app", SubscriptionFeature.MICRONUTRIENT_ANALYTICS);
    }}
