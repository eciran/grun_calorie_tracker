package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.WaterTrackingProperties;
import com.grun.calorietracker.dto.AchievementDto;
import com.grun.calorietracker.dto.AchievementSummaryDto;
import com.grun.calorietracker.entity.AchievementDefinitionEntity;
import com.grun.calorietracker.entity.DeviceDataEntity;
import com.grun.calorietracker.entity.ProgressLogEntity;
import com.grun.calorietracker.entity.StepGoalEntity;
import com.grun.calorietracker.entity.UserAchievementEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;
import com.grun.calorietracker.enums.FastingSessionStatus;
import com.grun.calorietracker.repository.AchievementDefinitionRepository;
import com.grun.calorietracker.repository.DeviceDataRepository;
import com.grun.calorietracker.repository.ExerciseLogRepository;
import com.grun.calorietracker.repository.FastingSessionRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.repository.ProgressLogRepository;
import com.grun.calorietracker.repository.StepGoalRepository;
import com.grun.calorietracker.repository.UserAchievementRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.repository.WaterLogRepository;
import com.grun.calorietracker.service.AchievementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AchievementServiceImpl implements AchievementService {

    public static final String PROFILE_COMPLETED = "PROFILE_COMPLETED";
    public static final String GOAL_SET = "GOAL_SET";
    public static final String FOOD_LOG_COUNT = "FOOD_LOG_COUNT";
    public static final String CORE_MEALS_SINGLE_DAY = "CORE_MEALS_SINGLE_DAY";
    public static final String FOOD_DISTINCT_DAYS = "FOOD_DISTINCT_DAYS";
    public static final String EXERCISE_LOG_COUNT = "EXERCISE_LOG_COUNT";
    public static final String EXERCISE_WEEKLY_BURN_CALORIES = "EXERCISE_WEEKLY_BURN_CALORIES";
    public static final String FASTING_COMPLETED_COUNT = "FASTING_COMPLETED_COUNT";
    public static final String FASTING_DISTINCT_COMPLETED_DAYS = "FASTING_DISTINCT_COMPLETED_DAYS";
    public static final String PROGRESS_LOG_COUNT = "PROGRESS_LOG_COUNT";
    public static final String WEIGHT_PROGRESS_KG = "WEIGHT_PROGRESS_KG";
    public static final String WATER_LOG_COUNT = "WATER_LOG_COUNT";
    public static final String WATER_TARGET_HIT_COUNT = "WATER_TARGET_HIT_COUNT";
    public static final String STEP_TARGET_HIT_DAYS = "STEP_TARGET_HIT_DAYS";
    public static final String STEP_CURRENT_STREAK_DAYS = "STEP_CURRENT_STREAK_DAYS";
    public static final String STEP_BEST_DAILY_STEPS = "STEP_BEST_DAILY_STEPS";

    private final AchievementDefinitionRepository achievementDefinitionRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final UserRepository userRepository;
    private final GoalRepository goalRepository;
    private final FoodLogsRepository foodLogsRepository;
    private final ExerciseLogRepository exerciseLogRepository;
    private final FastingSessionRepository fastingSessionRepository;
    private final ProgressLogRepository progressLogRepository;
    private final WaterLogRepository waterLogRepository;
    private final StepGoalRepository stepGoalRepository;
    private final DeviceDataRepository deviceDataRepository;
    private final WaterTrackingProperties waterTrackingProperties;

    @Override
    @Transactional
    public AchievementSummaryDto getMyAchievements(String email) {
        return evaluateMyAchievements(email);
    }

    @Override
    @Transactional
    public AchievementSummaryDto evaluateMyAchievements(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        List<AchievementDefinitionEntity> definitions = achievementDefinitionRepository.findByActiveTrueOrderBySortOrderAscCodeAsc();
        Map<String, UserAchievementEntity> existing = new HashMap<>();
        userAchievementRepository.findByUser(user)
                .forEach(achievement -> existing.put(achievement.getAchievementCode(), achievement));

        EvaluationSnapshot snapshot = snapshot(user);
        List<AchievementDto> achievements = definitions.stream()
                .map(definition -> evaluateDefinition(user, definition, existing, snapshot))
                .toList();

        int total = achievements.size();
        int unlocked = (int) achievements.stream().filter(item -> Boolean.TRUE.equals(item.getUnlocked())).count();
        int completionPercent = total == 0 ? 0 : (int) Math.round(unlocked * 100.0 / total);

        return AchievementSummaryDto.builder()
                .total(total)
                .unlocked(unlocked)
                .completionPercent(completionPercent)
                .achievements(achievements)
                .build();
    }

    private AchievementDto evaluateDefinition(UserEntity user,
                                              AchievementDefinitionEntity definition,
                                              Map<String, UserAchievementEntity> existing,
                                              EvaluationSnapshot snapshot) {
        int target = Math.max(1, definition.getTargetValue());
        int progress = Math.min(target, progressFor(definition.getMetricKey(), snapshot));
        boolean unlocked = progress >= target;
        LocalDateTime now = LocalDateTime.now();

        UserAchievementEntity userAchievement = existing.getOrDefault(definition.getCode(), new UserAchievementEntity());
        if (userAchievement.getId() == null) {
            userAchievement.setUser(user);
            userAchievement.setAchievementCode(definition.getCode());
        }
        boolean wasUnlocked = Boolean.TRUE.equals(userAchievement.getUnlocked());
        userAchievement.setProgressValue(progress);
        userAchievement.setTargetValue(target);
        userAchievement.setUnlocked(unlocked || wasUnlocked);
        if (!wasUnlocked && unlocked) {
            userAchievement.setUnlockedAt(now);
        }
        userAchievement.setLastEvaluatedAt(now);
        UserAchievementEntity saved = userAchievementRepository.save(userAchievement);

        int percent = (int) Math.round(Math.min(100.0, progress * 100.0 / target));
        return AchievementDto.builder()
                .code(definition.getCode())
                .title(definition.getTitle())
                .description(definition.getDescription())
                .metricKey(definition.getMetricKey())
                .category(definition.getCategory())
                .tier(definition.getTier())
                .progressValue(progress)
                .targetValue(target)
                .progressPercent(percent)
                .unlocked(saved.getUnlocked())
                .unlockedAt(saved.getUnlockedAt())
                .build();
    }

    private EvaluationSnapshot snapshot(UserEntity user) {
        UserGoalEntity goal = goalRepository.findByUser(user).orElse(null);
        long foodCount = foodLogsRepository.countByUser(user);
        long foodDays = foodLogsRepository.countDistinctLogDaysByUserId(user.getId());
        int maxCoreMealsDay = foodLogsRepository.maxCoreMealTypesLoggedInSingleDay(user.getId());
        long exerciseCount = exerciseLogRepository.countByUser(user);
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        double weeklyBurn = exerciseLogRepository.sumCaloriesBurnedByUserIdAndLogDateBetween(
                user.getId(),
                sevenDaysAgo,
                LocalDateTime.now().plusSeconds(1)
        );
        long completedFasts = fastingSessionRepository.countByUserAndStatusAndTargetReachedTrue(
                user,
                FastingSessionStatus.COMPLETED
        );
        long fastingDays = fastingSessionRepository.countDistinctCompletedTargetReachedDays(
                user.getId(),
                FastingSessionStatus.COMPLETED.name()
        );
        long progressCount = progressLogRepository.countByUser(user);
        int weightProgressKg = weightProgressKg(user, goal);
        long waterCount = waterLogRepository.countByUser(user);
        int maxWaterMl = waterLogRepository.maxDailyAmountMlByUserId(user.getId());
        StepMetrics stepMetrics = calculateStepMetrics(user);

        return new EvaluationSnapshot(
                isProfileComplete(user),
                goal != null,
                foodCount,
                foodDays,
                maxCoreMealsDay,
                exerciseCount,
                weeklyBurn,
                completedFasts,
                fastingDays,
                progressCount,
                weightProgressKg,
                waterCount,
                maxWaterMl,
                stepMetrics.targetHitDays(),
                stepMetrics.currentStreakDays(),
                stepMetrics.bestDailySteps()
        );
    }

    private int progressFor(String code, EvaluationSnapshot snapshot) {
        return switch (code) {
            case PROFILE_COMPLETED -> snapshot.profileComplete() ? 1 : 0;
            case GOAL_SET -> snapshot.goalSet() ? 1 : 0;
            case FOOD_LOG_COUNT -> safeLongToInt(snapshot.foodCount());
            case CORE_MEALS_SINGLE_DAY -> snapshot.maxCoreMealsDay();
            case FOOD_DISTINCT_DAYS -> safeLongToInt(snapshot.foodDays());
            case EXERCISE_LOG_COUNT -> safeLongToInt(snapshot.exerciseCount());
            case EXERCISE_WEEKLY_BURN_CALORIES -> (int) Math.floor(snapshot.weeklyBurnCalories());
            case FASTING_COMPLETED_COUNT -> safeLongToInt(snapshot.completedFasts());
            case FASTING_DISTINCT_COMPLETED_DAYS -> safeLongToInt(snapshot.fastingDays());
            case PROGRESS_LOG_COUNT -> safeLongToInt(snapshot.progressLogCount());
            case WEIGHT_PROGRESS_KG -> snapshot.weightProgressKg();
            case WATER_LOG_COUNT -> safeLongToInt(snapshot.waterLogCount());
            case WATER_TARGET_HIT_COUNT -> snapshot.maxWaterMl() >= waterTrackingProperties.getDefaultDailyTargetMl() ? 1 : 0;
            case STEP_TARGET_HIT_DAYS -> snapshot.stepTargetHitDays();
            case STEP_CURRENT_STREAK_DAYS -> snapshot.stepCurrentStreakDays();
            case STEP_BEST_DAILY_STEPS -> snapshot.stepBestDailySteps();
            default -> 0;
        };
    }

    private StepMetrics calculateStepMetrics(UserEntity user) {
        int targetSteps = stepGoalRepository.findByUser(user)
                .map(StepGoalEntity::getTargetSteps)
                .filter(Objects::nonNull)
                .orElse(10000);
        Map<java.time.LocalDate, Integer> stepsByDate = deviceDataRepository.findByUserOrderByRecordedAtAsc(user)
                .stream()
                .filter(metric -> metric.getSteps() != null && metric.getRecordedAt() != null)
                .collect(Collectors.groupingBy(
                        metric -> metric.getRecordedAt().toLocalDate(),
                        Collectors.summingInt(DeviceDataEntity::getSteps)
                ));
        int hitDays = (int) stepsByDate.values().stream().filter(steps -> steps >= targetSteps).count();
        int bestDailySteps = stepsByDate.values().stream().max(Integer::compareTo).orElse(0);
        int streak = 0;
        java.time.LocalDate cursor = java.time.LocalDate.now();
        java.time.LocalDate lowerBound = cursor.minusDays(365);
        while (!cursor.isBefore(lowerBound) && stepsByDate.getOrDefault(cursor, 0) >= targetSteps) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return new StepMetrics(hitDays, streak, bestDailySteps);
    }

    private int weightProgressKg(UserEntity user, UserGoalEntity goal) {
        if (goal == null || user.getWeight() == null || goal.getTargetWeight() == null) {
            return 0;
        }
        List<ProgressLogEntity> logs = progressLogRepository.findByUserOrderByLogDateAsc(user);
        if (logs.isEmpty()) {
            return 0;
        }
        double start = user.getWeight();
        double target = goal.getTargetWeight();
        double latest = logs.stream()
                .filter(log -> log.getWeight() != null)
                .reduce((first, second) -> second)
                .map(ProgressLogEntity::getWeight)
                .orElse(start);
        double totalNeeded = Math.abs(start - target);
        if (totalNeeded == 0) {
            return 0;
        }
        double movedTowardGoal = Math.abs(start - latest);
        boolean rightDirection = target < start ? latest < start : latest > start;
        return rightDirection ? (int) Math.floor(movedTowardGoal) : 0;
    }

    private boolean isProfileComplete(UserEntity user) {
        return user.getAge() != null
                && user.getGender() != null
                && user.getHeight() != null
                && user.getWeight() != null
                && user.getMarketRegion() != null
                && user.getPreferredLanguage() != null;
    }

    private int safeLongToInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private record EvaluationSnapshot(
            boolean profileComplete,
            boolean goalSet,
            long foodCount,
            long foodDays,
            int maxCoreMealsDay,
            long exerciseCount,
            double weeklyBurnCalories,
            long completedFasts,
            long fastingDays,
            long progressLogCount,
            int weightProgressKg,
            long waterLogCount,
            int maxWaterMl,
            int stepTargetHitDays,
            int stepCurrentStreakDays,
            int stepBestDailySteps
    ) {
    }

    private record StepMetrics(
            int targetHitDays,
            int currentStreakDays,
            int bestDailySteps
    ) {
    }
}
