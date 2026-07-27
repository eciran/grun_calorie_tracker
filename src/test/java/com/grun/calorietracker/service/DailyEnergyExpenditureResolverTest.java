package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.enums.EnergyExpenditureSource;
import com.grun.calorietracker.enums.HealthProvider;
import com.grun.calorietracker.service.support.DailyEnergyExpenditureResolver;
import com.grun.calorietracker.service.support.HealthDailyEnergySnapshot;
import com.grun.calorietracker.service.support.DailyLoggedActivitySnapshot;

import java.util.Map;
import com.grun.calorietracker.service.support.ProfileEnergyExpenditureCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DailyEnergyExpenditureResolverTest {

    private final DailyEnergyExpenditureResolver resolver = new DailyEnergyExpenditureResolver(
            new ProfileEnergyExpenditureCalculator());

    @Test
    void resolve_prefersProviderTotalOverProfileEstimate() {
        HealthDailyEnergySnapshot health = new HealthDailyEnergySnapshot(
                date(), HealthProvider.APPLE_HEALTH, 500.0, 1600.0, 2100.0,
                EnergyExpenditureSource.HEALTH_TOTAL_ENERGY);

        var day = resolver.resolve(date(), date(), List.of(health), completeUser(), ActivityLevel.MODERATE).get(0);

        assertEquals(2100.0, day.totalExpenditureCalories());
        assertEquals(EnergyExpenditureSource.HEALTH_TOTAL_ENERGY, day.source());
    }

    @Test
    void resolve_addsProfileRestingToProviderActiveWithoutUsingActivityMultiplier() {
        HealthDailyEnergySnapshot health = new HealthDailyEnergySnapshot(
                date(), HealthProvider.HEALTH_CONNECT, 420.0, null, null,
                EnergyExpenditureSource.UNAVAILABLE);

        var day = resolver.resolve(date(), date(), List.of(health), completeUser(), ActivityLevel.VERY_ACTIVE).get(0);

        assertEquals(1780.0, day.restingEnergyCalories());
        assertEquals(2200.0, day.totalExpenditureCalories());
        assertEquals(EnergyExpenditureSource.HEALTH_ACTIVE_PLUS_PROFILE_RESTING, day.source());
    }

    @Test
    void resolve_usesProfileTdeeWhenHealthTotalIsUnavailable() {
        var day = resolver.resolve(date(), date(), List.of(), completeUser(), ActivityLevel.SEDENTARY).get(0);

        assertEquals(2136.0, day.totalExpenditureCalories());
        assertEquals(EnergyExpenditureSource.PROFILE_TDEE_ESTIMATE, day.source());
    }

    @Test
    void resolve_addsManualExerciseAndStepCaloriesToHealthTotal() {
        HealthDailyEnergySnapshot health = new HealthDailyEnergySnapshot(
                date(), HealthProvider.APPLE_HEALTH, 200.0, 1600.0, 1800.0,
                EnergyExpenditureSource.HEALTH_TOTAL_ENERGY);
        DailyLoggedActivitySnapshot logged = new DailyLoggedActivitySnapshot(date(), 120.0, 80.0);

        var day = resolver.resolve(
                date(), date(), List.of(health), Map.of(date(), logged), completeUser(), ActivityLevel.MODERATE).get(0);

        assertEquals(400.0, day.activeEnergyCalories());
        assertEquals(2000.0, day.totalExpenditureCalories());
        assertEquals(EnergyExpenditureSource.HEALTH_TOTAL_PLUS_LOGGED_ACTIVITY, day.source());
    }

    @Test
    void resolve_addsLoggedActivityToProfileTdeeWithoutHealthData() {
        DailyLoggedActivitySnapshot logged = new DailyLoggedActivitySnapshot(date(), 120.0, 80.0);

        var day = resolver.resolve(
                date(), date(), List.of(), Map.of(date(), logged), completeUser(), ActivityLevel.VERY_ACTIVE).get(0);

        assertEquals(1780.0, day.restingEnergyCalories());
        assertEquals(200.0, day.activeEnergyCalories());
        assertEquals(3582.0, day.totalExpenditureCalories());
        assertEquals(EnergyExpenditureSource.PROFILE_TDEE_PLUS_LOGGED_ACTIVITY, day.source());
    }

    @Test
    void resolve_returnsUnavailableInsteadOfZeroWhenInputsAreIncomplete() {
        UserEntity incomplete = new UserEntity();

        var day = resolver.resolve(date(), date(), List.of(), incomplete, ActivityLevel.MODERATE).get(0);

        assertNull(day.totalExpenditureCalories());
        assertEquals(EnergyExpenditureSource.UNAVAILABLE, day.source());
    }

    private UserEntity completeUser() {
        UserEntity user = new UserEntity();
        user.setGender("MALE");
        user.setAge(30);
        user.setHeight(180.0);
        user.setWeight(80.0);
        return user;
    }

    private LocalDate date() {
        return LocalDate.of(2026, 7, 27);
    }
}