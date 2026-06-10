package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.WaterTrackingProperties;
import com.grun.calorietracker.dto.AchievementDto;
import com.grun.calorietracker.dto.AchievementSummaryDto;
import com.grun.calorietracker.entity.AchievementDefinitionEntity;
import com.grun.calorietracker.entity.ProgressLogEntity;
import com.grun.calorietracker.entity.UserAchievementEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;
import com.grun.calorietracker.enums.FastingSessionStatus;
import com.grun.calorietracker.repository.AchievementDefinitionRepository;
import com.grun.calorietracker.repository.ExerciseLogRepository;
import com.grun.calorietracker.repository.FastingSessionRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.repository.ProgressLogRepository;
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

    private final AchievementDefinitionRepository achievementDefinitionRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final UserRepository userRepository;
    private final GoalRepository goalRepository;
    private final FoodLogsRepository foodLogsRepository;
    private final ExerciseLogRepository exerciseLogRepository;
    private final FastingSessionRepository fastingSessionRepository;
    private final ProgressLogRepository progressLogRepository;
    private final WaterLogRepository waterLogRepository;
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
                maxWaterMl
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
            default -> 0;
        };
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
            int maxWaterMl
    ) {
    }
}
