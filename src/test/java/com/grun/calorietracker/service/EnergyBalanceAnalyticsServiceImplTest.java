package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.EnergyBalanceAnalyticsDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.EnergyExpenditureSource;
import com.grun.calorietracker.repository.BodyMeasurementRepository;
import com.grun.calorietracker.repository.DeviceDataRepository;
import com.grun.calorietracker.repository.ExerciseLogRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.service.impl.EnergyBalanceAnalyticsServiceImpl;
import com.grun.calorietracker.service.support.DailyEnergyExpenditureResolver;
import com.grun.calorietracker.service.support.DailyEnergyExpenditureSnapshot;
import com.grun.calorietracker.service.support.EnergyBalanceAnalyticsAssembler;
import com.grun.calorietracker.service.support.EnergyBalanceRequestGuard;
import com.grun.calorietracker.service.support.EnergyWeightModelCalculator;
import com.grun.calorietracker.service.support.HealthDailyEnergyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnergyBalanceAnalyticsServiceImplTest {

    @Mock private EnergyBalanceRequestGuard requestGuard;
    @Mock private FoodLogsRepository foodLogsRepository;
    @Mock private ExerciseLogRepository exerciseLogRepository;
    @Mock private DeviceDataRepository deviceDataRepository;
    @Mock private BodyMeasurementRepository bodyMeasurementRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private HealthDailyEnergyResolver healthDailyEnergyResolver;
    @Mock private DailyEnergyExpenditureResolver expenditureResolver;
    @Mock private EnergyWeightModelCalculator weightModelCalculator;
    @Mock private EnergyBalanceAnalyticsAssembler assembler;

    private EnergyBalanceAnalyticsServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        service = new EnergyBalanceAnalyticsServiceImpl(
                requestGuard,
                foodLogsRepository,
                exerciseLogRepository,
                deviceDataRepository,
                bodyMeasurementRepository,
                goalRepository,
                healthDailyEnergyResolver,
                expenditureResolver,
                weightModelCalculator,
                assembler
        );
        user = new UserEntity();
        user.setId(42L);
        user.setEmail("pro@grun.app");
        user.setTimeZone("Europe/Dublin");
    }

    @Test
    void getAnalytics_loadsInclusiveLocalRangeAndBuildsCanonicalResponse() {
        LocalDate startDate = LocalDate.of(2026, 7, 1);
        LocalDate endDate = LocalDate.of(2026, 7, 7);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();
        var context = new EnergyBalanceRequestGuard.RequestContext(
                user, startDate, endDate, 7, ZoneId.of("Europe/Dublin"));
        var expenditure = new DailyEnergyExpenditureSnapshot(
                startDate, 1600.0, 700.0, 2300.0, EnergyExpenditureSource.HEALTH_TOTAL_ENERGY);
        var weightModel = EnergyBalanceAnalyticsDto.WeightModel.builder().build();
        var expected = EnergyBalanceAnalyticsDto.builder().weightModel(weightModel).build();

        when(requestGuard.validate("pro@grun.app", startDate, endDate)).thenReturn(context);
        when(foodLogsRepository.getDailyStatsByUserAndDateBetween(42L, start, endExclusive))
                .thenReturn(Collections.singletonList(new Object[]{Date.valueOf(startDate), BigDecimal.valueOf(1800)}));
        when(deviceDataRepository.findByUserAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
                user, start, endExclusive)).thenReturn(List.of());
        when(goalRepository.findByUser(user)).thenReturn(Optional.empty());
        when(healthDailyEnergyResolver.resolve(List.of(), startDate, endDate)).thenReturn(List.of());
        when(expenditureResolver.resolve(startDate, endDate, List.of(), user, null))
                .thenReturn(List.of(expenditure));
        when(bodyMeasurementRepository.findByUserAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
                user, start, endExclusive)).thenReturn(List.of());
        when(weightModelCalculator.calculate(startDate, endDate, -500.0, 1, List.of()))
                .thenReturn(weightModel);
        when(foodLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                user, start, endExclusive)).thenReturn(List.of());
        when(exerciseLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                user, start, endExclusive)).thenReturn(List.of());
        when(assembler.assemble(eq(startDate), eq(endDate), eq("Europe/Dublin"),
                anyList(), eq(List.of(expenditure)), eq(List.of()), eq(List.of()), eq(weightModel)))
                .thenReturn(expected);

        EnergyBalanceAnalyticsDto result = service.getAnalytics("pro@grun.app", startDate, endDate);

        assertSame(expected, result);
        verify(weightModelCalculator).calculate(startDate, endDate, -500.0, 1, List.of());
        verify(assembler).assemble(eq(startDate), eq(endDate), eq("Europe/Dublin"),
                anyList(), eq(List.of(expenditure)), eq(List.of()), eq(List.of()), eq(weightModel));
    }

    @Test
    void getAnalytics_withoutPairedDays_passesNullBalanceToWeightModel() {
        LocalDate day = LocalDate.of(2026, 7, 7);
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime endExclusive = day.plusDays(1).atStartOfDay();
        var context = new EnergyBalanceRequestGuard.RequestContext(user, day, day, 1, ZoneId.of("UTC"));
        var unavailable = new DailyEnergyExpenditureSnapshot(
                day, null, null, null, EnergyExpenditureSource.UNAVAILABLE);
        var weightModel = EnergyBalanceAnalyticsDto.WeightModel.builder().build();
        var expected = EnergyBalanceAnalyticsDto.builder().build();

        when(requestGuard.validate("pro@grun.app", day, day)).thenReturn(context);
        when(foodLogsRepository.getDailyStatsByUserAndDateBetween(42L, start, endExclusive)).thenReturn(List.of());
        when(deviceDataRepository.findByUserAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
                user, start, endExclusive)).thenReturn(List.of());
        when(goalRepository.findByUser(user)).thenReturn(Optional.empty());
        when(healthDailyEnergyResolver.resolve(List.of(), day, day)).thenReturn(List.of());
        when(expenditureResolver.resolve(day, day, List.of(), user, null)).thenReturn(List.of(unavailable));
        when(bodyMeasurementRepository.findByUserAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
                user, start, endExclusive)).thenReturn(List.of());
        when(weightModelCalculator.calculate(day, day, null, 0, List.of())).thenReturn(weightModel);
        when(foodLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                user, start, endExclusive)).thenReturn(List.of());
        when(exerciseLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                user, start, endExclusive)).thenReturn(List.of());
        when(assembler.assemble(eq(day), eq(day), eq("UTC"), anyList(), anyList(), anyList(), anyList(), eq(weightModel)))
                .thenReturn(expected);

        assertSame(expected, service.getAnalytics("pro@grun.app", day, day));
        verify(weightModelCalculator).calculate(day, day, null, 0, List.of());
    }
}