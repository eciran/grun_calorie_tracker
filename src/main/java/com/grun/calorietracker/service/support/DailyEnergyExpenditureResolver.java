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
        return resolve(startDate, endDate, healthDays, Map.of(), user, activityLevel);
    }

    public List<DailyEnergyExpenditureSnapshot> resolve(
            LocalDate startDate,
            LocalDate endDate,
            List<HealthDailyEnergySnapshot> healthDays,
            Map<LocalDate, DailyLoggedActivitySnapshot> loggedActivityByDate,
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
                .map(date -> resolveDay(
                        date,
                        healthByDate.get(date),
                        loggedActivityByDate == null ? null : loggedActivityByDate.get(date),
                        resting.orElse(null),
                        profile.orElse(null)
                ))
                .toList();
    }

    private DailyEnergyExpenditureSnapshot resolveDay(
            LocalDate date,
            HealthDailyEnergySnapshot health,
            DailyLoggedActivitySnapshot loggedActivity,
            ProfileEnergyExpenditureCalculator.RestingEnergyEstimate resting,
            ProfileEnergyEstimate profile
    ) {
        double loggedCalories = loggedActivity == null ? 0.0 : loggedActivity.totalCalories();
        if (health != null && health.totalEnergyCalories() != null) {
            double active = safe(health.activeEnergyCalories()) + loggedCalories;
            return new DailyEnergyExpenditureSnapshot(
                    date,
                    health.restingEnergyCalories(),
                    round(active),
                    round(health.totalEnergyCalories() + loggedCalories),
                    loggedCalories > 0.0
                            ? EnergyExpenditureSource.HEALTH_TOTAL_PLUS_LOGGED_ACTIVITY
                            : health.expenditureSource()
            );
        }
        if (health != null && health.activeEnergyCalories() != null && resting != null) {
            double active = safe(health.activeEnergyCalories()) + loggedCalories;
            return new DailyEnergyExpenditureSnapshot(
                    date,
                    resting.restingEnergyCalories(),
                    round(active),
                    round(resting.restingEnergyCalories() + active),
                    loggedCalories > 0.0
                            ? EnergyExpenditureSource.HEALTH_ACTIVE_PLUS_PROFILE_RESTING_AND_LOGGED_ACTIVITY
                            : EnergyExpenditureSource.HEALTH_ACTIVE_PLUS_PROFILE_RESTING
            );
        }
        if (loggedCalories > 0.0 && profile != null) {
            return new DailyEnergyExpenditureSnapshot(
                    date,
                    profile.restingEnergyCalories(),
                    round(loggedCalories),
                    round(profile.totalDailyEnergyCalories() + loggedCalories),
                    EnergyExpenditureSource.PROFILE_TDEE_PLUS_LOGGED_ACTIVITY
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

    private double safe(Double value) {
        return value == null || !Double.isFinite(value) ? 0.0 : Math.max(0.0, value);
    }
}
