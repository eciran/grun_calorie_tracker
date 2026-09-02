package com.grun.calorietracker.service.reminder;

import com.grun.calorietracker.config.MealReminderDeliveryProperties;
import com.grun.calorietracker.entity.MealReminderPolicyEntity;
import com.grun.calorietracker.enums.MealReminderPolicyStatus;
import com.grun.calorietracker.repository.MealReminderPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MealReminderPolicyFactory {
    private final MealReminderDeliveryProperties properties;
    private final MealReminderPolicyRepository repository;

    public MealReminderPolicy current() {
        return repository.findFirstByStatus(MealReminderPolicyStatus.ACTIVE)
                .map(this::fromEntity).orElseGet(this::fromDeploymentDefaults);
    }

    public boolean isPilotUser(Long userId) {
        return repository.findFirstByStatus(MealReminderPolicyStatus.ACTIVE)
                .map(value -> java.util.Arrays.stream(value.getPilotUserIds().split(","))
                        .filter(item -> !item.isBlank()).map(Long::valueOf).anyMatch(userId::equals))
                .orElseGet(() -> properties.getPilotUserIds().contains(userId));
    }

    private MealReminderPolicy fromDeploymentDefaults() {
        MealReminderPolicy defaults = MealReminderPolicy.dryRunDefaults(properties.getPolicyVersion());
        return new MealReminderPolicy(
                defaults.version(), properties.getMode(), properties.isDeliveryEnabled(),
                properties.isKcalEnabled(), defaults.mealTimes(), defaults.slotAge(),
                defaults.maxDailyOccurrences(), defaults.maxRollingOccurrences(),
                defaults.maxDailyCatchups(), defaults.minimumReminderGap(),
                defaults.routineReminderGap(), defaults.defaultQuietStart(), defaults.defaultQuietEnd());
    }

    private MealReminderPolicy fromEntity(MealReminderPolicyEntity value) {
        MealReminderContract.Mode mode = value.isEmergencyStopped() ? MealReminderContract.Mode.OFF : value.getMode();
        return new MealReminderPolicy(value.getPolicyVersion(), mode, properties.isDeliveryEnabled(), value.isKcalEnabled(),
                java.util.Map.of(MealReminderContract.Meal.BREAKFAST, value.getBreakfastTime(),
                        MealReminderContract.Meal.LUNCH, value.getLunchTime(),
                        MealReminderContract.Meal.DINNER, value.getDinnerTime()),
                java.time.Duration.ofMinutes(value.getSlotAgeMinutes()), value.getMaxDaily(), value.getMaxRolling(),
                value.getMaxCatchups(), java.time.Duration.ofMinutes(value.getMinimumGapMinutes()),
                java.time.Duration.ofMinutes(value.getRoutineGapMinutes()), value.getQuietStart(), value.getQuietEnd());
    }
}
