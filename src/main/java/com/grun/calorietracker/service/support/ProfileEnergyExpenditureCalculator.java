package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.ActivityLevel;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ProfileEnergyExpenditureCalculator {

    public Optional<ProfileEnergyEstimate> calculate(UserEntity user, ActivityLevel activityLevel) {
        if (activityLevel == null) {
            return Optional.empty();
        }
        return calculateResting(user)
                .map(resting -> new ProfileEnergyEstimate(
                        resting.restingEnergyCalories(),
                        round(resting.restingEnergyCalories() * activityLevel.getMultiplier()),
                        resting.formula()
                ));
    }

    public Optional<RestingEnergyEstimate> calculateResting(UserEntity user) {
        if (!hasValidWeight(user)) {
            return Optional.empty();
        }
        if (user.getBodyFatPercentage() != null
                && Double.isFinite(user.getBodyFatPercentage())
                && user.getBodyFatPercentage() > 0.0
                && user.getBodyFatPercentage() < 100.0) {
            double leanBodyMassKg = user.getWeight() * (1.0 - user.getBodyFatPercentage() / 100.0);
            return Optional.of(new RestingEnergyEstimate(
                    round(370.0 + 21.6 * leanBodyMassKg),
                    "KATCH_MCARDLE"
            ));
        }

        if (user.getAge() == null || user.getAge() <= 0
                || user.getHeight() == null || !Double.isFinite(user.getHeight()) || user.getHeight() <= 0.0
                || user.getGender() == null) {
            return Optional.empty();
        }
        double sexOffset;
        if ("MALE".equalsIgnoreCase(user.getGender())) {
            sexOffset = 5.0;
        } else if ("FEMALE".equalsIgnoreCase(user.getGender())) {
            sexOffset = -161.0;
        } else {
            return Optional.empty();
        }
        double resting = 10.0 * user.getWeight()
                + 6.25 * user.getHeight()
                - 5.0 * user.getAge()
                + sexOffset;
        return Optional.of(new RestingEnergyEstimate(round(resting), "MIFFLIN_ST_JEOR"));
    }

    private boolean hasValidWeight(UserEntity user) {
        return user != null
                && user.getWeight() != null
                && Double.isFinite(user.getWeight())
                && user.getWeight() > 0.0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record RestingEnergyEstimate(double restingEnergyCalories, String formula) {
    }
}