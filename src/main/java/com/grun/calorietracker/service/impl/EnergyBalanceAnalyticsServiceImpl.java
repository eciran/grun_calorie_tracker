package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.EnergyBalanceAnalyticsDto;
import com.grun.calorietracker.entity.BodyMeasurementEntity;
import com.grun.calorietracker.entity.DeviceDataEntity;
import com.grun.calorietracker.entity.ExerciseLogsEntity;
import com.grun.calorietracker.entity.FoodLogsEntity;
import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.repository.BodyMeasurementRepository;
import com.grun.calorietracker.repository.DeviceDataRepository;
import com.grun.calorietracker.repository.ExerciseLogRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.service.EnergyBalanceAnalyticsService;
import com.grun.calorietracker.service.support.DailyCalorieIntakeSnapshot;
import com.grun.calorietracker.service.support.DailyEnergyExpenditureResolver;
import com.grun.calorietracker.service.support.DailyEnergyExpenditureSnapshot;
import com.grun.calorietracker.service.support.EnergyBalanceAnalyticsAssembler;
import com.grun.calorietracker.service.support.EnergyBalanceRequestGuard;
import com.grun.calorietracker.service.support.EnergyWeightModelCalculator;
import com.grun.calorietracker.service.support.HealthDailyEnergyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnergyBalanceAnalyticsServiceImpl implements EnergyBalanceAnalyticsService {

    private final EnergyBalanceRequestGuard requestGuard;
    private final FoodLogsRepository foodLogsRepository;
    private final ExerciseLogRepository exerciseLogRepository;
    private final DeviceDataRepository deviceDataRepository;
    private final BodyMeasurementRepository bodyMeasurementRepository;
    private final GoalRepository goalRepository;
    private final HealthDailyEnergyResolver healthDailyEnergyResolver;
    private final DailyEnergyExpenditureResolver expenditureResolver;
    private final EnergyWeightModelCalculator weightModelCalculator;
    private final EnergyBalanceAnalyticsAssembler assembler;

    @Override
    @Transactional(readOnly = true)
    public EnergyBalanceAnalyticsDto getAnalytics(String email, LocalDate startDate, LocalDate endDate) {
        EnergyBalanceRequestGuard.RequestContext context = requestGuard.validate(email, startDate, endDate);
        LocalDateTime start = context.startDate().atStartOfDay();
        LocalDateTime endExclusive = context.endDate().plusDays(1).atStartOfDay();

        List<DailyCalorieIntakeSnapshot> intakeDays = loadDailyIntake(
                context.user().getId(), start, endExclusive);
        List<DeviceDataEntity> healthMetrics = deviceDataRepository
                .findByUserAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
                        context.user(), start, endExclusive);
        ActivityLevel activityLevel = goalRepository.findByUser(context.user())
                .map(goal -> goal.getActivityLevel())
                .orElse(null);
        List<DailyEnergyExpenditureSnapshot> expenditureDays = expenditureResolver.resolve(
                context.startDate(),
                context.endDate(),
                healthDailyEnergyResolver.resolve(healthMetrics, context.startDate(), context.endDate()),
                context.user(),
                activityLevel
        );

        BalanceTotals totals = calculateBalanceTotals(intakeDays, expenditureDays);
        List<BodyMeasurementEntity> measurements = bodyMeasurementRepository
                .findByUserAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
                        context.user(), start, endExclusive);
        EnergyBalanceAnalyticsDto.WeightModel weightModel = weightModelCalculator.calculate(
                context.startDate(),
                context.endDate(),
                totals.cumulativeBalanceCalories(),
                totals.evaluatedDays(),
                measurements
        );

        List<FoodLogsEntity> foodLogs = foodLogsRepository
                .findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                        context.user(), start, endExclusive);
        List<ExerciseLogsEntity> exerciseLogs = exerciseLogRepository
                .findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                        context.user(), start, endExclusive);

        return assembler.assemble(
                context.startDate(),
                context.endDate(),
                context.zoneId().getId(),
                intakeDays,
                expenditureDays,
                foodLogs,
                exerciseLogs,
                weightModel
        );
    }

    private List<DailyCalorieIntakeSnapshot> loadDailyIntake(
            Long userId,
            LocalDateTime start,
            LocalDateTime endExclusive
    ) {
        return foodLogsRepository.getDailyStatsByUserAndDateBetween(userId, start, endExclusive)
                .stream()
                .filter(row -> row != null && row.length > 1)
                .map(row -> new DailyCalorieIntakeSnapshot(
                        toLocalDate(row[0]),
                        toDouble(row[1]),
                        true
                ))
                .filter(snapshot -> snapshot.date() != null)
                .toList();
    }

    private BalanceTotals calculateBalanceTotals(
            List<DailyCalorieIntakeSnapshot> intakeDays,
            List<DailyEnergyExpenditureSnapshot> expenditureDays
    ) {
        Map<LocalDate, DailyCalorieIntakeSnapshot> intakeByDate = intakeDays.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(DailyCalorieIntakeSnapshot::date, Function.identity(), (left, right) -> right));
        double cumulative = 0.0;
        int evaluatedDays = 0;
        for (DailyEnergyExpenditureSnapshot expenditure : expenditureDays) {
            if (expenditure == null || expenditure.totalExpenditureCalories() == null) {
                continue;
            }
            DailyCalorieIntakeSnapshot intake = intakeByDate.get(expenditure.date());
            if (intake == null || !intake.foodLogged() || intake.consumedCalories() == null) {
                continue;
            }
            cumulative += intake.consumedCalories() - expenditure.totalExpenditureCalories();
            evaluatedDays++;
        }
        return new BalanceTotals(evaluatedDays == 0 ? null : round(cumulative), evaluatedDays);
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        return value == null ? null : LocalDate.parse(value.toString());
    }

    private Double toDouble(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        double result = number.doubleValue();
        return Double.isFinite(result) ? Math.max(0.0, round(result)) : null;
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private record BalanceTotals(Double cumulativeBalanceCalories, int evaluatedDays) {
    }
}