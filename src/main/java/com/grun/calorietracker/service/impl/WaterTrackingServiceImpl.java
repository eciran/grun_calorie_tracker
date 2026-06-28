package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.WaterTrackingProperties;
import com.grun.calorietracker.dto.WaterDailySummaryDto;
import com.grun.calorietracker.dto.WaterDailyTrendDto;
import com.grun.calorietracker.dto.WaterGoalDto;
import com.grun.calorietracker.dto.WaterGoalRequestDto;
import com.grun.calorietracker.dto.WaterLogDto;
import com.grun.calorietracker.dto.WaterLogRequestDto;
import com.grun.calorietracker.dto.WaterRangeSummaryDto;
import com.grun.calorietracker.dto.WaterReminderSettingsDto;
import com.grun.calorietracker.dto.WaterReminderSettingsRequestDto;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.WaterLogEntity;
import com.grun.calorietracker.entity.WaterReminderSettingsEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.repository.WaterLogRepository;
import com.grun.calorietracker.repository.WaterReminderSettingsRepository;
import com.grun.calorietracker.service.PushDeliveryService;
import com.grun.calorietracker.service.WaterTrackingService;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WaterTrackingServiceImpl implements WaterTrackingService {

    private static final String WATER_REMINDER_TYPE = "water_reminder";
    private static final String WATER_REMINDER_MESSAGE = "Time to drink water.";
    private static final int MIN_REMINDER_INTERVAL_MINUTES = 30;
    private static final int MAX_RANGE_DAYS = 366;

    private final WaterLogRepository waterLogRepository;
    private final WaterReminderSettingsRepository waterReminderSettingsRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final WaterTrackingProperties waterTrackingProperties;
    private final UserTimeZoneSupport userTimeZoneSupport;
    private final PushDeliveryService pushDeliveryService;

    @Override
    @Transactional
    public WaterLogDto addWaterLog(String email, WaterLogRequestDto request) {
        UserEntity user = getUser(email);
        validateLogDate(request, user);
        WaterLogEntity entity = new WaterLogEntity();
        entity.setUser(user);
        entity.setLogDate(request.getLogDate());
        entity.setAmountMl(request.getAmountMl());
        entity.setSource(normalizeSource(request.getSource()));
        entity.setLoggedAt(resolveLoggedAt(request, user));
        return toDto(waterLogRepository.save(entity));
    }

    @Override
    @Transactional
    public WaterLogDto updateWaterLog(String email, Long id, WaterLogRequestDto request) {
        UserEntity user = getUser(email);
        validateLogDate(request, user);
        WaterLogEntity entity = waterLogRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Water log not found"));
        entity.setLogDate(request.getLogDate());
        entity.setAmountMl(request.getAmountMl());
        entity.setSource(normalizeSource(request.getSource()));
        entity.setLoggedAt(resolveLoggedAt(request, user));
        return toDto(waterLogRepository.save(entity));
    }
    @Override
    @Transactional(readOnly = true)
    public WaterDailySummaryDto getDailySummary(String email, LocalDate date) {
        UserEntity user = getUser(email);
        List<WaterLogDto> logs = waterLogRepository.findByUserAndLogDateOrderByLoggedAtAsc(user, date)
                .stream()
                .map(this::toDto)
                .toList();
        long totalMlLong = waterLogRepository.sumAmountMlByUserAndLogDate(user, date);
        int totalMl = totalMlLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalMlLong;
        int targetMl = resolveTargetMl(user);

        WaterDailySummaryDto summary = new WaterDailySummaryDto();
        summary.setDate(date);
        summary.setTotalMl(totalMl);
        summary.setTargetMl(targetMl);
        summary.setRemainingMl(Math.max(0, targetMl - totalMl));
        summary.setProgressPercent(round(Math.min(100.0, totalMl * 100.0 / targetMl)));
        summary.setLogs(logs);
        return summary;
    }


    @Override
    @Transactional(readOnly = true)
    public WaterRangeSummaryDto getRangeSummary(String email, LocalDate startDate, LocalDate endDate) {
        UserEntity user = getUser(email);
        LocalDate resolvedEnd = endDate == null ? userTimeZoneSupport.today(user) : endDate;
        LocalDate resolvedStart = startDate == null ? resolvedEnd.minusDays(6) : startDate;
        validateRange(resolvedStart, resolvedEnd);

        int targetMl = resolveTargetMl(user);
        Map<LocalDate, Integer> totalsByDate = waterLogRepository.aggregateDailyWaterByUser(user, resolvedStart, resolvedEnd)
                .stream()
                .collect(Collectors.toMap(
                        row -> (LocalDate) row[0],
                        row -> toInt(row[2]),
                        Integer::sum
                ));

        List<WaterDailyTrendDto> days = resolvedStart.datesUntil(resolvedEnd.plusDays(1))
                .map(date -> toTrendDto(date, totalsByDate.getOrDefault(date, 0), targetMl))
                .toList();

        int totalMl = days.stream().map(WaterDailyTrendDto::getTotalMl).mapToInt(Integer::intValue).sum();
        WaterRangeSummaryDto dto = new WaterRangeSummaryDto();
        dto.setStartDate(resolvedStart);
        dto.setEndDate(resolvedEnd);
        dto.setTargetMl(targetMl);
        dto.setDays(days);
        dto.setDayCount(days.size());
        dto.setTotalMl(totalMl);
        dto.setAverageMl(round(days.isEmpty() ? 0.0 : totalMl / (double) days.size()));
        dto.setBestMl(days.stream().map(WaterDailyTrendDto::getTotalMl).max(Integer::compareTo).orElse(0));
        dto.setTargetHitDays((int) days.stream().filter(day -> Boolean.TRUE.equals(day.getTargetReached())).count());
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public WaterGoalDto getGoal(String email) {
        UserEntity user = getUser(email);
        return toGoalDto(getOrDefaultSettings(user));
    }

    @Override
    @Transactional
    public WaterGoalDto updateGoal(String email, WaterGoalRequestDto request) {
        UserEntity user = getUser(email);
        WaterReminderSettingsEntity settings = getOrDefaultSettings(user);
        settings.setDailyTargetMl(request.getTargetMl());
        return toGoalDto(waterReminderSettingsRepository.save(settings));
    }

    @Override
    @Transactional
    public void deleteWaterLog(String email, Long id) {
        UserEntity user = getUser(email);
        WaterLogEntity entity = waterLogRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Water log not found"));
        waterLogRepository.delete(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public WaterReminderSettingsDto getReminderSettings(String email) {
        UserEntity user = getUser(email);
        return toReminderSettingsDto(getOrDefaultSettings(user));
    }

    @Override
    @Transactional
    public WaterReminderSettingsDto updateReminderSettings(String email, WaterReminderSettingsRequestDto request) {
        validateReminderSettingsRequest(request);
        UserEntity user = getUser(email);
        WaterReminderSettingsEntity settings = waterReminderSettingsRepository.findByUser(user)
                .orElseGet(() -> defaultSettings(user));
        settings.setEnabled(request.getEnabled());
        settings.setIntervalMinutes(request.getIntervalMinutes());
        settings.setStartTime(request.getStartTime());
        settings.setEndTime(request.getEndTime());
        if (!Boolean.TRUE.equals(request.getEnabled())) {
            settings.setLastReminderAt(null);
        }
        return toReminderSettingsDto(waterReminderSettingsRepository.save(settings));
    }

    @Override
    @Scheduled(fixedDelayString = "${grun.water.reminders.scan-interval-ms:300000}")
    @Transactional
    public int createDueReminderNotifications() {
        if (!waterTrackingProperties.getReminders().isEnabled()) {
            return 0;
        }
        List<WaterReminderSettingsEntity> dueSettings = waterReminderSettingsRepository
                .findByEnabledTrue()
                .stream()
                .filter(this::isDue)
                .toList();

        dueSettings.forEach(settings -> {
            LocalDateTime userNow = userTimeZoneSupport.now(settings.getUser());
            NotificationEntity notification = new NotificationEntity();
            notification.setUser(settings.getUser());
            notification.setType(WATER_REMINDER_TYPE);
            notification.setMessage(WATER_REMINDER_MESSAGE);
            notification.setIsRead(false);
            notification.setCreatedAt(userNow);
            NotificationEntity saved = notificationRepository.save(notification);
            pushDeliveryService.deliver(saved);
            settings.setLastReminderAt(userNow);
        });
        waterReminderSettingsRepository.saveAll(dueSettings);
        return dueSettings.size();
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private void validateLogDate(WaterLogRequestDto request, UserEntity user) {
        LocalDate today = userTimeZoneSupport.today(user);
        if (request.getLogDate().isAfter(today)) {
            throw new IllegalArgumentException("Water log date cannot be in the future.");
        }
    }

    private void validateReminderSettingsRequest(WaterReminderSettingsRequestDto request) {
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new IllegalArgumentException("Water reminder startTime must be before endTime.");
        }
    }

    private LocalDateTime resolveLoggedAt(WaterLogRequestDto request, UserEntity user) {
        if (request.getLoggedAt() != null) {
            return request.getLogDate().atTime(request.getLoggedAt().toLocalTime());
        }
        return request.getLogDate().atTime(userTimeZoneSupport.currentTime(user));
    }

    private WaterReminderSettingsEntity getOrDefaultSettings(UserEntity user) {
        return waterReminderSettingsRepository.findByUser(user).orElseGet(() -> defaultSettings(user));
    }

    private WaterReminderSettingsEntity defaultSettings(UserEntity user) {
        WaterReminderSettingsEntity settings = new WaterReminderSettingsEntity();
        settings.setUser(user);
        settings.setEnabled(false);
        settings.setIntervalMinutes(120);
        settings.setStartTime(LocalTime.of(9, 0));
        settings.setEndTime(LocalTime.of(21, 0));
        settings.setDailyTargetMl(waterTrackingProperties.getDefaultDailyTargetMl());
        return settings;
    }

    private boolean isDue(WaterReminderSettingsEntity settings) {
        LocalDateTime now = userTimeZoneSupport.now(settings.getUser());
        LocalTime currentTime = now.toLocalTime();
        if (currentTime.isBefore(settings.getStartTime()) || !currentTime.isBefore(settings.getEndTime())) {
            return false;
        }
        return settings.getLastReminderAt() == null
                || !settings.getLastReminderAt().plusMinutes(settings.getIntervalMinutes()).isAfter(now);
    }

    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Water range startDate must be before or equal to endDate.");
        }
        long dayCount = startDate.datesUntil(endDate.plusDays(1)).count();
        if (dayCount > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Water range cannot exceed " + MAX_RANGE_DAYS + " days.");
        }
    }

    private WaterDailyTrendDto toTrendDto(LocalDate date, int totalMl, int targetMl) {
        WaterDailyTrendDto dto = new WaterDailyTrendDto();
        dto.setDate(date);
        dto.setTotalMl(totalMl);
        dto.setTargetMl(targetMl);
        dto.setRemainingMl(Math.max(0, targetMl - totalMl));
        dto.setProgressPercent(round(Math.min(100.0, totalMl * 100.0 / targetMl)));
        dto.setTargetReached(totalMl >= targetMl);
        return dto;
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            long longValue = number.longValue();
            return longValue > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) longValue;
        }
        return Integer.parseInt(value.toString());
    }

    private int resolveTargetMl(UserEntity user) {
        return waterReminderSettingsRepository.findByUser(user)
                .map(WaterReminderSettingsEntity::getDailyTargetMl)
                .filter(target -> target != null && target > 0)
                .orElse(waterTrackingProperties.getDefaultDailyTargetMl());
    }

    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return "MANUAL";
        }
        return source.trim().toUpperCase();
    }

    private WaterLogDto toDto(WaterLogEntity entity) {
        WaterLogDto dto = new WaterLogDto();
        dto.setId(entity.getId());
        dto.setLogDate(entity.getLogDate());
        dto.setAmountMl(entity.getAmountMl());
        dto.setSource(entity.getSource());
        dto.setLoggedAt(entity.getLoggedAt());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private WaterGoalDto toGoalDto(WaterReminderSettingsEntity entity) {
        WaterGoalDto dto = new WaterGoalDto();
        dto.setTargetMl(entity.getDailyTargetMl() == null ? waterTrackingProperties.getDefaultDailyTargetMl() : entity.getDailyTargetMl());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private WaterReminderSettingsDto toReminderSettingsDto(WaterReminderSettingsEntity entity) {
        WaterReminderSettingsDto dto = new WaterReminderSettingsDto();
        dto.setId(entity.getId());
        dto.setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        dto.setIntervalMinutes(entity.getIntervalMinutes());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setLastReminderAt(entity.getLastReminderAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
