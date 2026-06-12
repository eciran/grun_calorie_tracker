package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminTrackingSummaryDto;
import com.grun.calorietracker.enums.FastingSessionStatus;
import com.grun.calorietracker.repository.DeviceDataRepository;
import com.grun.calorietracker.repository.FastingPlanRepository;
import com.grun.calorietracker.repository.FastingSessionRepository;
import com.grun.calorietracker.repository.StepGoalRepository;
import com.grun.calorietracker.repository.WaterLogRepository;
import com.grun.calorietracker.repository.WaterReminderSettingsRepository;
import com.grun.calorietracker.service.AdminTrackingMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminTrackingMonitoringServiceImpl implements AdminTrackingMonitoringService {

    private static final int DEFAULT_DAYS = 30;
    private static final int MIN_DAYS = 7;
    private static final int MAX_DAYS = 90;

    private final WaterLogRepository waterLogRepository;
    private final WaterReminderSettingsRepository waterReminderSettingsRepository;
    private final FastingSessionRepository fastingSessionRepository;
    private final FastingPlanRepository fastingPlanRepository;
    private final DeviceDataRepository deviceDataRepository;
    private final StepGoalRepository stepGoalRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminTrackingSummaryDto getSummary(Integer days) {
        int rangeDays = clamp(days == null ? DEFAULT_DAYS : days);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(rangeDays - 1L);
        LocalDateTime since = startDate.atStartOfDay();

        AdminTrackingSummaryDto.TrackingModuleSummaryDto water = new AdminTrackingSummaryDto.TrackingModuleSummaryDto(
                "water",
                waterLogRepository.countByLoggedAtAfter(since),
                waterLogRepository.countDistinctUsersByLoggedAtAfter(since),
                waterLogRepository.sumAmountMlByLoggedAtAfter(since),
                "ml",
                waterReminderSettingsRepository.count(),
                waterReminderSettingsRepository.countByEnabledTrue(),
                0
        );

        AdminTrackingSummaryDto.TrackingModuleSummaryDto fasting = new AdminTrackingSummaryDto.TrackingModuleSummaryDto(
                "fasting",
                fastingSessionRepository.countByStartedAtAfter(since),
                fastingSessionRepository.countDistinctUsersByStartedAtAfter(since),
                fastingSessionRepository.sumActualMinutesByStartedAtAfter(since),
                "minutes",
                fastingPlanRepository.countByActiveTrue(),
                fastingPlanRepository.countByReminderEnabledTrue(),
                fastingSessionRepository.countByStatus(FastingSessionStatus.ACTIVE)
        );

        AdminTrackingSummaryDto.TrackingModuleSummaryDto steps = new AdminTrackingSummaryDto.TrackingModuleSummaryDto(
                "steps",
                deviceDataRepository.countStepRecordsAfter(since),
                deviceDataRepository.countDistinctStepUsersAfter(since),
                deviceDataRepository.sumStepsAfter(since),
                "steps",
                stepGoalRepository.count(),
                stepGoalRepository.countByReminderEnabledTrue(),
                0
        );

        Map<LocalDate, AdminTrackingSummaryDto.TrackingTrendPointDto> trends = initializeTrendMap(startDate, endDate);
        applyWaterTrend(trends, waterLogRepository.aggregateDailyWater(startDate, endDate));
        applyFastingTrend(trends, fastingSessionRepository.aggregateDailyFasting(startDate, endDate));
        applyStepTrend(trends, deviceDataRepository.aggregateDailySteps(startDate, endDate));

        return new AdminTrackingSummaryDto(
                LocalDateTime.now(),
                rangeDays,
                startDate,
                endDate,
                water,
                fasting,
                steps,
                trends.values().stream().toList()
        );
    }

    private int clamp(int days) {
        return Math.max(MIN_DAYS, Math.min(MAX_DAYS, days));
    }

    private Map<LocalDate, AdminTrackingSummaryDto.TrackingTrendPointDto> initializeTrendMap(LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, AdminTrackingSummaryDto.TrackingTrendPointDto> trends = new LinkedHashMap<>();
        startDate.datesUntil(endDate.plusDays(1)).forEach(date -> trends.put(
                date,
                new AdminTrackingSummaryDto.TrackingTrendPointDto(date, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        ));
        return trends;
    }

    private void applyWaterTrend(Map<LocalDate, AdminTrackingSummaryDto.TrackingTrendPointDto> trends, List<Object[]> rows) {
        for (Object[] row : rows) {
            AdminTrackingSummaryDto.TrackingTrendPointDto point = trends.get(toLocalDate(row[0]));
            if (point == null) {
                continue;
            }
            point.setWaterLogs(toLong(row[1]));
            point.setWaterUsers(toLong(row[2]));
            point.setWaterMl(toLong(row[3]));
        }
    }

    private void applyFastingTrend(Map<LocalDate, AdminTrackingSummaryDto.TrackingTrendPointDto> trends, List<Object[]> rows) {
        for (Object[] row : rows) {
            AdminTrackingSummaryDto.TrackingTrendPointDto point = trends.get(toLocalDate(row[0]));
            if (point == null) {
                continue;
            }
            point.setFastingSessions(toLong(row[1]));
            point.setFastingUsers(toLong(row[2]));
            point.setFastingMinutes(toLong(row[3]));
        }
    }

    private void applyStepTrend(Map<LocalDate, AdminTrackingSummaryDto.TrackingTrendPointDto> trends, List<Object[]> rows) {
        for (Object[] row : rows) {
            AdminTrackingSummaryDto.TrackingTrendPointDto point = trends.get(toLocalDate(row[0]));
            if (point == null) {
                continue;
            }
            point.setStepRecords(toLong(row[1]));
            point.setStepUsers(toLong(row[2]));
            point.setSteps(toLong(row[3]));
        }
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }
}
