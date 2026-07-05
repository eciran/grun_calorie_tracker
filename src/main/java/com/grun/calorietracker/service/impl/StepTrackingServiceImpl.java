package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.StepDailySummaryDto;
import com.grun.calorietracker.dto.StepGoalDto;
import com.grun.calorietracker.dto.StepGoalRequestDto;
import com.grun.calorietracker.dto.StepManualLogRequestDto;
import com.grun.calorietracker.dto.StepManualLogResponseDto;
import com.grun.calorietracker.dto.StepRangeSummaryDto;
import com.grun.calorietracker.entity.DeviceDataEntity;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.StepGoalEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.HealthProvider;
import com.grun.calorietracker.exception.DuplicateManualStepLogException;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.DeviceDataRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.StepGoalRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.PushDeliveryService;
import com.grun.calorietracker.service.StepTrackingService;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class StepTrackingServiceImpl implements StepTrackingService {

    private static final int DEFAULT_TARGET_STEPS = 10000;
    private static final int MAX_RANGE_DAYS = 366;
    private static final int MAX_DAILY_TOTAL_STEPS = 120000;
    private static final String STEP_REMINDER_TYPE = "step_reminder";
    private static final String STEP_REMINDER_MESSAGE = "You are below your step target. A short walk can help you close the gap.";

    private final UserRepository userRepository;
    private final StepGoalRepository stepGoalRepository;
    private final DeviceDataRepository deviceDataRepository;
    private final NotificationRepository notificationRepository;
    private final PushDeliveryService pushDeliveryService;
    private final UserTimeZoneSupport userTimeZoneSupport;

    @Override
    @Transactional(readOnly = true)
    public StepGoalDto getGoal(String email) {
        UserEntity user = getUser(email);
        return toGoalDto(stepGoalRepository.findByUser(user).orElseGet(() -> defaultGoal(user)));
    }

    @Override
    @Transactional
    public StepGoalDto updateGoal(String email, StepGoalRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Step goal request is required.");
        }
        UserEntity user = getUser(email);
        StepGoalEntity goal = stepGoalRepository.findByUser(user).orElseGet(() -> defaultGoal(user));
        if (request.getTargetSteps() != null) {
            goal.setTargetSteps(request.getTargetSteps());
        }
        if (request.getReminderEnabled() != null) {
            goal.setReminderEnabled(request.getReminderEnabled());
        }
        if (request.getReminderTime() != null) {
            goal.setReminderTime(request.getReminderTime());
        }
        if (request.getReminderThresholdPercent() != null) {
            goal.setReminderThresholdPercent(request.getReminderThresholdPercent());
        }
        return toGoalDto(stepGoalRepository.save(goal));
    }

    @Override
    @Transactional(readOnly = true)
    public StepDailySummaryDto getDailySummary(String email, LocalDate date) {
        UserEntity user = getUser(email);
        LocalDate summaryDate = date == null ? userTimeZoneSupport.today(user) : date;
        return buildDailySummary(user, summaryDate, getTargetSteps(user), true);
    }

    @Override
    @Transactional(readOnly = true)
    public StepRangeSummaryDto getRangeSummary(String email, LocalDate startDate, LocalDate endDate) {
        UserEntity user = getUser(email);
        LocalDate resolvedEnd = endDate == null ? userTimeZoneSupport.today(user) : endDate;
        LocalDate resolvedStart = startDate == null ? resolvedEnd.minusDays(6) : startDate;
        validateRange(resolvedStart, resolvedEnd);

        int targetSteps = getTargetSteps(user);
        Map<LocalDate, DailyStepAggregate> aggregates = aggregateStepsByDate(user, resolvedStart, resolvedEnd);
        List<StepDailySummaryDto> days = resolvedStart.datesUntil(resolvedEnd.plusDays(1))
                .map(date -> toDailySummaryFromAggregate(date, targetSteps, aggregates.get(date)))
                .toList();

        StepRangeSummaryDto dto = new StepRangeSummaryDto();
        dto.setStartDate(resolvedStart);
        dto.setEndDate(resolvedEnd);
        dto.setTargetSteps(targetSteps);
        dto.setDays(days);
        dto.setDayCount(days.size());
        dto.setTotalSteps(days.stream().map(StepDailySummaryDto::getTotalSteps).filter(Objects::nonNull).mapToInt(Integer::intValue).sum());
        dto.setAverageSteps(round(days.isEmpty() ? 0.0 : dto.getTotalSteps() / (double) days.size()));
        dto.setBestSteps(days.stream().map(StepDailySummaryDto::getTotalSteps).filter(Objects::nonNull).max(Integer::compareTo).orElse(0));
        dto.setTargetHitDays((int) days.stream().filter(day -> Boolean.TRUE.equals(day.getTargetReached())).count());
        return dto;
    }
    @Override
    @Transactional(readOnly = true)
    public List<StepManualLogResponseDto> getManualLogs(String email, LocalDate startDate, LocalDate endDate) {
        UserEntity user = getUser(email);
        LocalDate resolvedEnd = endDate == null ? userTimeZoneSupport.today(user) : endDate;
        LocalDate resolvedStart = startDate == null ? resolvedEnd : startDate;
        validateRange(resolvedStart, resolvedEnd);
        return deviceDataRepository.findByUserAndProviderAndRecordedAtBetweenOrderByRecordedAtAsc(
                        user,
                        HealthProvider.MANUAL,
                        resolvedStart.atStartOfDay(),
                        resolvedEnd.plusDays(1).atStartOfDay()
                )
                .stream()
                .filter(metric -> metric.getSteps() != null)
                .map(this::toManualLogResponse)
                .toList();
    }

    @Override
    @Transactional
    public StepManualLogResponseDto addManualLog(String email, StepManualLogRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Manual step log request is required.");
        }
        UserEntity user = getUser(email);
        validateManualLog(user, request, null);

        DeviceDataEntity entity = new DeviceDataEntity();
        applyManualLog(entity, user, request);
        DeviceDataEntity saved = deviceDataRepository.save(entity);
        return toManualLogResponse(saved);
    }

    @Override
    @Transactional
    public StepManualLogResponseDto updateManualLog(String email, Long id, StepManualLogRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Manual step log request is required.");
        }
        UserEntity user = getUser(email);
        DeviceDataEntity entity = deviceDataRepository.findByIdAndUserAndProvider(id, user, HealthProvider.MANUAL)
                .orElseThrow(() -> new ResourceNotFoundException("Manual step log not found."));
        validateManualLog(user, request, entity);
        applyManualLog(entity, user, request);
        DeviceDataEntity saved = deviceDataRepository.save(entity);
        return toManualLogResponse(saved);
    }

    @Override
    @Transactional
    public void deleteManualLog(String email, Long id) {
        UserEntity user = getUser(email);
        DeviceDataEntity entity = deviceDataRepository.findByIdAndUserAndProvider(id, user, HealthProvider.MANUAL)
                .orElseThrow(() -> new ResourceNotFoundException("Manual step log not found."));
        deviceDataRepository.delete(entity);
    }

    @Override
    @Scheduled(fixedDelayString = "${grun.steps.reminders.scan-interval-ms:300000}")
    @Transactional
    public int createDueReminderNotifications() {
        List<StepGoalEntity> dueGoals = stepGoalRepository.findByReminderEnabledTrue()
                .stream()
                .filter(this::isReminderDue)
                .toList();

        dueGoals.forEach(goal -> {
            LocalDateTime userNow = userTimeZoneSupport.now(goal.getUser());
            NotificationEntity notification = new NotificationEntity();
            notification.setUser(goal.getUser());
            notification.setType(STEP_REMINDER_TYPE);
            notification.setMessage(STEP_REMINDER_MESSAGE);
            notification.setIsRead(false);
            notification.setCreatedAt(userNow);
            NotificationEntity saved = notificationRepository.save(notification);
            pushDeliveryService.deliver(saved);
            goal.setLastReminderAt(userNow);
        });
        stepGoalRepository.saveAll(dueGoals);
        return dueGoals.size();
    }

    private StepManualLogResponseDto toManualLogResponse(DeviceDataEntity entity) {
        return new StepManualLogResponseDto(
                entity.getId(),
                entity.getSteps(),
                entity.getDistanceMeters(),
                entity.getCaloriesBurned(),
                entity.getRecordedAt()
        );
    }
    private void applyManualLog(DeviceDataEntity entity, UserEntity user, StepManualLogRequestDto request) {
        entity.setUser(user);
        entity.setProvider(HealthProvider.MANUAL);
        entity.setSource("MANUAL");
        entity.setExternalId(null);
        entity.setSteps(request.getSteps());
        entity.setDistanceMeters(request.getDistanceMeters());
        entity.setCaloriesBurned(request.getCaloriesBurned());
        entity.setRecordedAt(request.getRecordedAt());
        entity.setHeartRate(null);
        entity.setSleepHours(null);
    }

    private void validateManualLog(UserEntity user, StepManualLogRequestDto request, DeviceDataEntity existingLog) {
        if (request.getRecordedAt() == null) {
            throw new IllegalArgumentException("Manual step recordedAt is required.");
        }
        LocalDate today = userTimeZoneSupport.today(user);
        if (request.getRecordedAt().toLocalDate().isAfter(today)) {
            throw new IllegalArgumentException("Manual step log date cannot be in the future.");
        }
        validateDuplicateManualLog(user, request, existingLog);
        validateDailyTotalLimit(user, request, existingLog);
    }

    private void validateDuplicateManualLog(UserEntity user, StepManualLogRequestDto request, DeviceDataEntity existingLog) {
        deviceDataRepository.findByUserAndProviderAndExternalIdIsNullAndRecordedAt(user, HealthProvider.MANUAL, request.getRecordedAt())
                .filter(found -> existingLog == null || !Objects.equals(found.getId(), existingLog.getId()))
                .ifPresent(found -> {
                    throw new DuplicateManualStepLogException("Manual step log already exists for this recordedAt.");
                });
    }

    private void validateDailyTotalLimit(UserEntity user, StepManualLogRequestDto request, DeviceDataEntity existingLog) {
        LocalDate logDate = request.getRecordedAt().toLocalDate();
        LocalDateTime start = logDate.atStartOfDay();
        LocalDateTime end = logDate.plusDays(1).atStartOfDay();
        long currentTotal = deviceDataRepository.sumStepsByUserAndRecordedAtRange(user, start, end);
        if (existingLog != null
                && existingLog.getRecordedAt() != null
                && existingLog.getRecordedAt().toLocalDate().equals(logDate)) {
            currentTotal -= existingLog.getSteps() == null ? 0 : existingLog.getSteps();
        }
        long requestedTotal = currentTotal + request.getSteps();
        if (requestedTotal > MAX_DAILY_TOTAL_STEPS) {
            throw new IllegalArgumentException("Daily step total cannot exceed " + MAX_DAILY_TOTAL_STEPS + " steps.");
        }
    }

    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Step range startDate must be before or equal to endDate.");
        }
        long dayCount = startDate.datesUntil(endDate.plusDays(1)).count();
        if (dayCount > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Step range cannot exceed " + MAX_RANGE_DAYS + " days.");
        }
    }

    private Map<LocalDate, DailyStepAggregate> aggregateStepsByDate(UserEntity user, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, DailyStepAggregate> byDate = new HashMap<>();
        deviceDataRepository.aggregateDailyStepsByUser(user.getId(), startDate, endDate)
                .forEach(row -> byDate.put(toLocalDate(row[0]), new DailyStepAggregate(
                        toInt(row[1]),
                        toDouble(row[2]),
                        toDouble(row[3]),
                        toLocalDateTime(row[4]),
                        toInt(row[5])
                )));
        return byDate;
    }

    private StepDailySummaryDto toDailySummaryFromAggregate(LocalDate date, int targetSteps, DailyStepAggregate aggregate) {
        int totalSteps = aggregate == null ? 0 : aggregate.totalSteps();
        StepDailySummaryDto dto = new StepDailySummaryDto();
        dto.setDate(date);
        dto.setTotalSteps(totalSteps);
        dto.setTargetSteps(targetSteps);
        dto.setRemainingSteps(Math.max(targetSteps - totalSteps, 0));
        dto.setProgressPercent(percent(totalSteps, targetSteps));
        dto.setTargetReached(totalSteps >= targetSteps);
        dto.setTotalDistanceMeters(round(aggregate == null ? 0.0 : aggregate.totalDistanceMeters()));
        dto.setTotalCaloriesBurned(round(aggregate == null ? 0.0 : aggregate.totalCaloriesBurned()));
        dto.setHasStepData(aggregate != null && aggregate.recordCount() > 0);
        dto.setLatestStepAt(aggregate == null ? null : aggregate.latestStepAt());
        dto.setProviders(List.of());
        dto.setCurrentStreakDays(null);
        return dto;
    }

    private StepDailySummaryDto buildDailySummary(UserEntity user, LocalDate date, int targetSteps, boolean includeStreak) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        List<DeviceDataEntity> metrics = deviceDataRepository
                .findByUserAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(user, start, end);

        int totalSteps = metrics.stream().map(DeviceDataEntity::getSteps).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        StepDailySummaryDto dto = new StepDailySummaryDto();
        dto.setDate(date);
        dto.setTotalSteps(totalSteps);
        dto.setTargetSteps(targetSteps);
        dto.setRemainingSteps(Math.max(targetSteps - totalSteps, 0));
        dto.setProgressPercent(percent(totalSteps, targetSteps));
        dto.setTargetReached(totalSteps >= targetSteps);
        dto.setTotalDistanceMeters(round(metrics.stream().map(DeviceDataEntity::getDistanceMeters).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum()));
        dto.setTotalCaloriesBurned(round(metrics.stream().map(DeviceDataEntity::getCaloriesBurned).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum()));
        dto.setHasStepData(metrics.stream().anyMatch(metric -> metric.getSteps() != null));
        dto.setLatestStepAt(metrics.stream()
                .filter(metric -> metric.getSteps() != null)
                .map(DeviceDataEntity::getRecordedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null));
        dto.setProviders(metrics.stream()
                .filter(metric -> metric.getSteps() != null)
                .map(DeviceDataEntity::getProvider)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        dto.setCurrentStreakDays(includeStreak ? calculateTargetStreak(user, date, targetSteps) : null);
        return dto;
    }

    private int calculateTargetStreak(UserEntity user, LocalDate date, int targetSteps) {
        int streak = 0;
        LocalDate cursor = date;
        LocalDate lowerBound = date.minusDays(365);
        while (!cursor.isBefore(lowerBound)) {
            StepDailySummaryDto summary = buildDailySummary(user, cursor, targetSteps, false);
            if (!Boolean.TRUE.equals(summary.getTargetReached())) {
                break;
            }
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private int getTargetSteps(UserEntity user) {
        return stepGoalRepository.findByUser(user).map(StepGoalEntity::getTargetSteps).orElse(DEFAULT_TARGET_STEPS);
    }

    private StepGoalEntity defaultGoal(UserEntity user) {
        StepGoalEntity goal = new StepGoalEntity();
        goal.setUser(user);
        goal.setTargetSteps(DEFAULT_TARGET_STEPS);
        goal.setReminderEnabled(false);
        goal.setReminderTime(java.time.LocalTime.of(20, 0));
        goal.setReminderThresholdPercent(70);
        return goal;
    }

    private StepGoalDto toGoalDto(StepGoalEntity entity) {
        StepGoalDto dto = new StepGoalDto();
        dto.setTargetSteps(entity.getTargetSteps());
        dto.setReminderEnabled(entity.getReminderEnabled());
        dto.setReminderTime(entity.getReminderTime());
        dto.setReminderThresholdPercent(entity.getReminderThresholdPercent());
        dto.setLastReminderAt(entity.getLastReminderAt());
        return dto;
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private Double percent(int value, int target) {
        if (target <= 0) {
            return 0.0;
        }
        return round((value * 100.0) / target);
    }

    private Double round(Double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        return ((Number) value).intValue();
    }

    private double toDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        return ((Number) value).doubleValue();
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }

    private boolean isReminderDue(StepGoalEntity goal) {
        UserEntity user = goal.getUser();
        if (user == null
                || !Boolean.TRUE.equals(user.getPushNotificationsEnabled())
                || !Boolean.TRUE.equals(user.getStepRemindersEnabled())) {
            return false;
        }
        LocalDateTime userNow = userTimeZoneSupport.now(user);
        if (goal.getReminderTime() != null && userNow.toLocalTime().isBefore(goal.getReminderTime())) {
            return false;
        }
        if (goal.getLastReminderAt() != null && goal.getLastReminderAt().toLocalDate().equals(userNow.toLocalDate())) {
            return false;
        }
        StepDailySummaryDto summary = buildDailySummary(user, userNow.toLocalDate(), getTargetSteps(user), false);
        int threshold = goal.getReminderThresholdPercent() == null ? 70 : goal.getReminderThresholdPercent();
        return summary.getProgressPercent() < threshold;
    }

    private record DailyStepAggregate(
            int totalSteps,
            double totalDistanceMeters,
            double totalCaloriesBurned,
            LocalDateTime latestStepAt,
            int recordCount
    ) {
    }
}