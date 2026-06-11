package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.WaterTrackingProperties;
import com.grun.calorietracker.dto.WaterDailySummaryDto;
import com.grun.calorietracker.dto.WaterLogDto;
import com.grun.calorietracker.dto.WaterLogRequestDto;
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

@Service
@RequiredArgsConstructor
public class WaterTrackingServiceImpl implements WaterTrackingService {

    private static final String WATER_REMINDER_TYPE = "water_reminder";
    private static final String WATER_REMINDER_MESSAGE = "Time to drink water.";
    private static final int MIN_REMINDER_INTERVAL_MINUTES = 30;

    private final WaterLogRepository waterLogRepository;
    private final WaterReminderSettingsRepository waterReminderSettingsRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final WaterTrackingProperties waterTrackingProperties;
    private final UserTimeZoneSupport userTimeZoneSupport;

    @Override
    @Transactional
    public WaterLogDto addWaterLog(String email, WaterLogRequestDto request) {
        validateRequest(request);
        UserEntity user = getUser(email);
        WaterLogEntity entity = new WaterLogEntity();
        entity.setUser(user);
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
        int targetMl = Math.max(1, waterTrackingProperties.getDefaultDailyTargetMl());

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
            notificationRepository.save(notification);
            settings.setLastReminderAt(userNow);
        });
        waterReminderSettingsRepository.saveAll(dueSettings);
        return dueSettings.size();
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private void validateRequest(WaterLogRequestDto request) {
        if (request.getLoggedAt() != null && !request.getLoggedAt().toLocalDate().equals(request.getLogDate())) {
            throw new IllegalArgumentException("loggedAt date must match logDate.");
        }
    }

    private void validateReminderSettingsRequest(WaterReminderSettingsRequestDto request) {
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new IllegalArgumentException("Water reminder startTime must be before endTime.");
        }
    }

    private LocalDateTime resolveLoggedAt(WaterLogRequestDto request, UserEntity user) {
        if (request.getLoggedAt() != null) {
            return request.getLoggedAt();
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
