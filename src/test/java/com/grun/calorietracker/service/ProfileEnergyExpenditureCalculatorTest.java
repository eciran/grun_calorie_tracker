package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.service.support.ProfileEnergyExpenditureCalculator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileEnergyExpenditureCalculatorTest {

    private final ProfileEnergyExpenditureCalculator calculator = new ProfileEnergyExpenditureCalculator();

    @Test
    void calculate_usesMifflinStJeorAndActivityMultiplier() {
        UserEntity user = user("MALE", 30, 180.0, 80.0, null);

        var result = calculator.calculate(user, ActivityLevel.SEDENTARY).orElseThrow();

        assertEquals(1780.0, result.restingEnergyCalories());
        assertEquals(2136.0, result.totalDailyEnergyCalories());
        assertEquals("MIFFLIN_ST_JEOR", result.formula());
    }

    @Test
    void calculate_usesKatchMcArdleWhenValidBodyFatExists() {
        UserEntity user = user("FEMALE", 35, 165.0, 70.0, 25.0);

        var result = calculator.calculate(user, ActivityLevel.MODERATE).orElseThrow();

        assertEquals(1504.0, result.restingEnergyCalories());
        assertEquals(2331.2, result.totalDailyEnergyCalories());
        assertEquals("KATCH_MCARDLE", result.formula());
    }

    @Test
    void calculateResting_allowsKatchMcArdleWithoutMifflinOnlyFields() {
        UserEntity user = new UserEntity();
        user.setWeight(70.0);
        user.setBodyFatPercentage(25.0);

        var result = calculator.calculateResting(user).orElseThrow();

        assertEquals(1504.0, result.restingEnergyCalories());
        assertEquals("KATCH_MCARDLE", result.formula());
    }
    @Test
    void calculate_returnsEmptyForIncompleteOrUnsupportedProfile() {
        assertTrue(calculator.calculate(user(null, 30, 180.0, 80.0, null), ActivityLevel.MODERATE).isEmpty());
        assertTrue(calculator.calculate(user("MALE", 30, 180.0, 80.0, null), null).isEmpty());
    }

    private UserEntity user(String gender, int age, double height, double weight, Double bodyFat) {
        UserEntity user = new UserEntity();
        user.setGender(gender);
        user.setAge(age);
        user.setHeight(height);
        user.setWeight(weight);
        user.setBodyFatPercentage(bodyFat);
        return user;
    }
}