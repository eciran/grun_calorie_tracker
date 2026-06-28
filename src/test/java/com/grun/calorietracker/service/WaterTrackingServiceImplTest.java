package com.grun.calorietracker.service;

import com.grun.calorietracker.config.WaterTrackingProperties;
import com.grun.calorietracker.dto.WaterDailySummaryDto;
import com.grun.calorietracker.dto.WaterGoalRequestDto;
import com.grun.calorietracker.dto.WaterLogDto;
import com.grun.calorietracker.dto.WaterLogRequestDto;
import com.grun.calorietracker.dto.WaterReminderSettingsRequestDto;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.WaterLogEntity;
import com.grun.calorietracker.entity.WaterReminderSettingsEntity;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.repository.WaterLogRepository;
import com.grun.calorietracker.repository.WaterReminderSettingsRepository;
import com.grun.calorietracker.service.impl.WaterTrackingServiceImpl;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.Mock;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class WaterTrackingServiceImplTest {

    @Mock
    private WaterLogRepository waterLogRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WaterReminderSettingsRepository waterReminderSettingsRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private PushDeliveryService pushDeliveryService;

    private WaterTrackingServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        WaterTrackingProperties properties = new WaterTrackingProperties();
        properties.setDefaultDailyTargetMl(2500);
        service = new WaterTrackingServiceImpl(
                waterLogRepository,
                waterReminderSettingsRepository,
                notificationRepository,
                userRepository,
                properties,
                new UserTimeZoneSupport(),
                pushDeliveryService
        );

        user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@grun.app");
        user.setTimeZone("Europe/Dublin");
    }

    @Test
    void addWaterLog_savesManualEntry() {
        WaterLogRequestDto request = new WaterLogRequestDto();
        request.setLogDate(LocalDate.of(2026, 6, 5));
        request.setAmountMl(250);
        request.setSource("quick_add");
        request.setLoggedAt(LocalDateTime.of(2026, 6, 5, 10, 15));

        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(waterLogRepository.save(any(WaterLogEntity.class))).thenAnswer(invocation -> {
            WaterLogEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            entity.setCreatedAt(LocalDateTime.of(2026, 6, 5, 10, 16));
            return entity;
        });

        WaterLogDto result = service.addWaterLog("user@grun.app", request);

        assertEquals(10L, result.getId());
        assertEquals(250, result.getAmountMl());
        assertEquals("QUICK_ADD", result.getSource());
        assertEquals(LocalDate.of(2026, 6, 5), result.getLogDate());
        assertEquals(LocalDate.of(2026, 6, 5), result.getLoggedAt().toLocalDate());
        verify(waterLogRepository).save(any(WaterLogEntity.class));
    }

    @Test
    void addWaterLog_whenLoggedAtIsMissing_usesLogDateWithCurrentTime() {
        WaterLogRequestDto request = new WaterLogRequestDto();
        request.setLogDate(LocalDate.of(2026, 6, 5));
        request.setAmountMl(250);

        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(waterLogRepository.save(any(WaterLogEntity.class))).thenAnswer(invocation -> {
            WaterLogEntity entity = invocation.getArgument(0);
            entity.setId(11L);
            entity.setCreatedAt(LocalDateTime.of(2026, 6, 5, 10, 16));
            return entity;
        });

        WaterLogDto result = service.addWaterLog("user@grun.app", request);

        assertEquals(LocalDate.of(2026, 6, 5), result.getLoggedAt().toLocalDate());
    }

    @Test
    void getDailySummary_calculatesTargetProgressAndRemainingAmount() {
        WaterLogEntity first = waterLog(1L, 250);
        WaterLogEntity second = waterLog(2L, 500);
        LocalDate date = LocalDate.of(2026, 6, 5);

        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(waterLogRepository.findByUserAndLogDateOrderByLoggedAtAsc(user, date))
                .thenReturn(List.of(first, second));
        when(waterLogRepository.sumAmountMlByUserAndLogDate(user, date)).thenReturn(750L);

        WaterDailySummaryDto result = service.getDailySummary("user@grun.app", date);

        assertEquals(750, result.getTotalMl());
        assertEquals(2500, result.getTargetMl());
        assertEquals(1750, result.getRemainingMl());
        assertEquals(30.0, result.getProgressPercent());
        assertEquals(2, result.getLogs().size());
    }

    @Test
    void getDailySummary_usesUserWaterGoal() {
        LocalDate date = LocalDate.of(2026, 6, 5);
        WaterReminderSettingsEntity settings = reminderSettings();
        settings.setDailyTargetMl(3000);

        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(waterLogRepository.findByUserAndLogDateOrderByLoggedAtAsc(user, date)).thenReturn(List.of());
        when(waterLogRepository.sumAmountMlByUserAndLogDate(user, date)).thenReturn(1200L);
        when(waterReminderSettingsRepository.findByUser(user)).thenReturn(Optional.of(settings));

        WaterDailySummaryDto result = service.getDailySummary("user@grun.app", date);

        assertEquals(3000, result.getTargetMl());
        assertEquals(1800, result.getRemainingMl());
        assertEquals(40.0, result.getProgressPercent());
    }

    @Test
    void getRangeSummary_returnsDailyTrendsAndAggregates() {
        LocalDate startDate = LocalDate.of(2026, 6, 5);
        LocalDate endDate = LocalDate.of(2026, 6, 7);
        WaterReminderSettingsEntity settings = reminderSettings();
        settings.setDailyTargetMl(2000);

        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(waterReminderSettingsRepository.findByUser(user)).thenReturn(Optional.of(settings));
        when(waterLogRepository.aggregateDailyWaterByUser(user, startDate, endDate)).thenReturn(List.of(
                new Object[]{LocalDate.of(2026, 6, 5), 2L, 1500L},
                new Object[]{LocalDate.of(2026, 6, 7), 1L, 2500L}
        ));

        var result = service.getRangeSummary("user@grun.app", startDate, endDate);

        assertEquals(3, result.getDayCount());
        assertEquals(4000, result.getTotalMl());
        assertEquals(1333.33, result.getAverageMl());
        assertEquals(2500, result.getBestMl());
        assertEquals(1, result.getTargetHitDays());
        assertEquals(0, result.getDays().get(1).getTotalMl());
    }

    @Test
    void getRangeSummary_whenRangeTooLarge_rejectsRequest() {
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class, () -> service.getRangeSummary(
                "user@grun.app",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 6, 5)
        ));
    }
    @Test
    void updateGoal_savesDailyTarget() {
        WaterGoalRequestDto request = new WaterGoalRequestDto();
        request.setTargetMl(3200);
        WaterReminderSettingsEntity settings = reminderSettings();

        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(waterReminderSettingsRepository.findByUser(user)).thenReturn(Optional.of(settings));
        when(waterReminderSettingsRepository.save(any(WaterReminderSettingsEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.updateGoal("user@grun.app", request);

        assertEquals(3200, result.getTargetMl());
        verify(waterReminderSettingsRepository).save(any(WaterReminderSettingsEntity.class));
    }

    @Test
    void addWaterLog_whenLoggedAtDateDiffersFromLogDate_usesLogDateWithLoggedAtTime() {
        WaterLogRequestDto request = new WaterLogRequestDto();
        request.setLogDate(LocalDate.of(2026, 6, 5));
        request.setAmountMl(250);
        request.setLoggedAt(LocalDateTime.of(2026, 6, 6, 0, 5));
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(waterLogRepository.save(any(WaterLogEntity.class))).thenAnswer(invocation -> {
            WaterLogEntity entity = invocation.getArgument(0);
            entity.setId(12L);
            return entity;
        });

        WaterLogDto result = service.addWaterLog("user@grun.app", request);

        assertEquals(LocalDateTime.of(2026, 6, 5, 0, 5), result.getLoggedAt());
    }

    @Test
    void addWaterLog_whenLogDateIsInFuture_rejectsRequest() {
        WaterLogRequestDto request = new WaterLogRequestDto();
        request.setLogDate(new UserTimeZoneSupport().today(user).plusDays(1));
        request.setAmountMl(250);

        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class, () -> service.addWaterLog("user@grun.app", request));
        verify(waterLogRepository, never()).save(any(WaterLogEntity.class));
    }


    @Test
    void updateWaterLog_whenOwnedLogExists_updatesIt() {
        WaterLogRequestDto request = new WaterLogRequestDto();
        request.setLogDate(LocalDate.of(2026, 6, 6));
        request.setAmountMl(400);
        request.setSource("manual_edit");
        request.setLoggedAt(LocalDateTime.of(2026, 6, 7, 11, 30));
        WaterLogEntity entity = waterLog(9L, 250);

        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(waterLogRepository.findByIdAndUser(9L, user)).thenReturn(Optional.of(entity));
        when(waterLogRepository.save(any(WaterLogEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WaterLogDto result = service.updateWaterLog("user@grun.app", 9L, request);

        assertEquals(9L, result.getId());
        assertEquals(400, result.getAmountMl());
        assertEquals("MANUAL_EDIT", result.getSource());
        assertEquals(LocalDate.of(2026, 6, 6), result.getLogDate());
        assertEquals(LocalDateTime.of(2026, 6, 6, 11, 30), result.getLoggedAt());
        verify(waterLogRepository).save(entity);
    }

    @Test
    void updateWaterLog_whenMissing_throwsNotFound() {
        WaterLogRequestDto request = new WaterLogRequestDto();
        request.setLogDate(LocalDate.of(2026, 6, 5));
        request.setAmountMl(400);

        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(waterLogRepository.findByIdAndUser(9L, user)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.updateWaterLog("user@grun.app", 9L, request));
    }
    @Test
    void deleteWaterLog_whenOwnedLogExists_deletesIt() {
        WaterLogEntity entity = waterLog(9L, 300);
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(waterLogRepository.findByIdAndUser(9L, user)).thenReturn(Optional.of(entity));

        service.deleteWaterLog("user@grun.app", 9L);

        verify(waterLogRepository).delete(entity);
    }

    @Test
    void deleteWaterLog_whenMissing_throwsNotFound() {
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(waterLogRepository.findByIdAndUser(9L, user)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.deleteWaterLog("user@grun.app", 9L));
    }

    @Test
    void updateReminderSettings_savesUserPreference() {
        WaterReminderSettingsRequestDto request = new WaterReminderSettingsRequestDto();
        request.setEnabled(true);
        request.setIntervalMinutes(120);
        request.setStartTime(LocalTime.of(9, 0));
        request.setEndTime(LocalTime.of(21, 0));

        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(waterReminderSettingsRepository.findByUser(user)).thenReturn(Optional.empty());
        when(waterReminderSettingsRepository.save(any(WaterReminderSettingsEntity.class))).thenAnswer(invocation -> {
            WaterReminderSettingsEntity entity = invocation.getArgument(0);
            entity.setId(7L);
            return entity;
        });

        var result = service.updateReminderSettings("user@grun.app", request);

        assertEquals(7L, result.getId());
        assertEquals(true, result.getEnabled());
        assertEquals(120, result.getIntervalMinutes());
        verify(waterReminderSettingsRepository).save(any(WaterReminderSettingsEntity.class));
    }

    @Test
    void updateReminderSettings_whenTimeWindowInvalid_rejectsRequest() {
        WaterReminderSettingsRequestDto request = new WaterReminderSettingsRequestDto();
        request.setEnabled(true);
        request.setIntervalMinutes(120);
        request.setStartTime(LocalTime.of(21, 0));
        request.setEndTime(LocalTime.of(9, 0));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateReminderSettings("user@grun.app", request));
    }

    @Test
    void createDueReminderNotifications_whenReminderIsDue_createsNotification() {
        WaterReminderSettingsEntity settings = reminderSettings();
        settings.setLastReminderAt(LocalDateTime.now().minusMinutes(130));
        when(waterReminderSettingsRepository.findByEnabledTrue())
                .thenReturn(List.of(settings));

        int created = service.createDueReminderNotifications();

        assertEquals(1, created);
        verify(notificationRepository).save(any(NotificationEntity.class));
        verify(waterReminderSettingsRepository).saveAll(List.of(settings));
    }

    @Test
    void createDueReminderNotifications_whenSchedulerDisabled_doesNotCreateNotification() {
        WaterTrackingProperties properties = new WaterTrackingProperties();
        properties.getReminders().setEnabled(false);
        service = new WaterTrackingServiceImpl(
                waterLogRepository,
                waterReminderSettingsRepository,
                notificationRepository,
                userRepository,
                properties,
                new UserTimeZoneSupport(),
                pushDeliveryService
        );

        int created = service.createDueReminderNotifications();

        assertEquals(0, created);
        verify(waterReminderSettingsRepository, never()).findByEnabledTrue();
        verify(notificationRepository, never()).save(any(NotificationEntity.class));
    }

    @Test
    void createDueReminderNotifications_whenReminderIsNotDue_doesNotCreateNotification() {
        WaterReminderSettingsEntity settings = reminderSettings();
        settings.setLastReminderAt(LocalDateTime.now().minusMinutes(40));
        when(waterReminderSettingsRepository.findByEnabledTrue())
                .thenReturn(List.of(settings));

        int created = service.createDueReminderNotifications();

        assertEquals(0, created);
        verify(notificationRepository, never()).save(any(NotificationEntity.class));
    }

    private WaterLogEntity waterLog(Long id, Integer amountMl) {
        WaterLogEntity entity = new WaterLogEntity();
        entity.setId(id);
        entity.setUser(user);
        entity.setLogDate(LocalDate.of(2026, 6, 5));
        entity.setAmountMl(amountMl);
        entity.setSource("MANUAL");
        entity.setLoggedAt(LocalDateTime.of(2026, 6, 5, 10, 0).plusMinutes(id));
        entity.setCreatedAt(entity.getLoggedAt());
        return entity;
    }

    private WaterReminderSettingsEntity reminderSettings() {
        WaterReminderSettingsEntity settings = new WaterReminderSettingsEntity();
        settings.setId(1L);
        settings.setUser(user);
        settings.setEnabled(true);
        settings.setIntervalMinutes(120);
        settings.setStartTime(LocalTime.MIN);
        settings.setEndTime(LocalTime.MAX);
        return settings;
    }
}
