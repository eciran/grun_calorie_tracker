package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.enums.EnergyExpenditureSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DailyEnergyExpenditureResolver {

    private final ProfileEnergyExpenditureCalculator profileCalculator;

    public List<DailyEnergyExpenditureSnapshot> resolve(
            LocalDate startDate,
            LocalDate endDate,
            List<HealthDailyEnergySnapshot> healthDays,
            UserEntity user,
            ActivityLevel activityLevel
    ) {
        Map<LocalDate, HealthDailyEnergySnapshot> healthByDate = healthDays == null
                ? Map.of()
                : healthDays.stream().collect(Collectors.toMap(
                        HealthDailyEnergySnapshot::date,
                        Function.identity(),
                        (first, ignored) -> first
                ));
        var resting = profileCalculator.calculateResting(user);
        var profile = profileCalculator.calculate(user, activityLevel);

        return startDate.datesUntil(endDate.plusDays(1))
                .map(date -> resolveDay(date, healthByDate.get(date), resting.orElse(null), profile.orElse(null)))
                .toList();
    }

    private DailyEnergyExpenditureSnapshot resolveDay(
            LocalDate date,
            HealthDailyEnergySnapshot health,
            ProfileEnergyExpenditureCalculator.RestingEnergyEstimate resting,
            ProfileEnergyEstimate profile
    ) {
        if (health != null && health.totalEnergyCalories() != null) {
            return new DailyEnergyExpenditureSnapshot(
                    date,
                    health.restingEnergyCalories(),
                    health.activeEnergyCalories(),
                    health.totalEnergyCalories(),
                    health.expenditureSource()
            );
        }
        if (health != null && health.activeEnergyCalories() != null && resting != null) {
            return new DailyEnergyExpenditureSnapshot(
                    date,
                    resting.restingEnergyCalories(),
                    health.activeEnergyCalories(),
                    round(resting.restingEnergyCalories() + health.activeEnergyCalories()),
                    EnergyExpenditureSource.HEALTH_ACTIVE_PLUS_PROFILE_RESTING
            );
        }
        if (profile != null) {
            return new DailyEnergyExpenditureSnapshot(
                    date,
                    profile.restingEnergyCalories(),
                    null,
                    profile.totalDailyEnergyCalories(),
                    EnergyExpenditureSource.PROFILE_TDEE_ESTIMATE
            );
        }
        return new DailyEnergyExpenditureSnapshot(date, null, null, null, EnergyExpenditureSource.UNAVAILABLE);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}