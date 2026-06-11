package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.HealthConnectionDto;
import com.grun.calorietracker.dto.HealthConnectionRequestDto;
import com.grun.calorietracker.dto.HealthDataDeleteResponseDto;
import com.grun.calorietracker.dto.HealthDailySummaryDto;
import com.grun.calorietracker.dto.HealthMetricBatchSyncRequestDto;
import com.grun.calorietracker.dto.HealthMetricBatchSyncResponseDto;
import com.grun.calorietracker.dto.HealthMetricSyncRequestDto;
import com.grun.calorietracker.dto.HealthMetricSyncResponseDto;
import com.grun.calorietracker.dto.HealthRangeSummaryDto;
import com.grun.calorietracker.entity.DeviceDataEntity;
import com.grun.calorietracker.entity.HealthConnectionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.HealthConnectionStatus;
import com.grun.calorietracker.enums.HealthProvider;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.DeviceDataRepository;
import com.grun.calorietracker.repository.HealthConnectionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.HealthIntegrationService;
import com.grun.calorietracker.service.SubscriptionService;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class HealthIntegrationServiceImpl implements HealthIntegrationService {

    private static final int MAX_HEALTH_METRICS_PER_BATCH = 500;

    private final UserRepository userRepository;
    private final HealthConnectionRepository healthConnectionRepository;
    private final DeviceDataRepository deviceDataRepository;
    private final SubscriptionService subscriptionService;
    private final UserTimeZoneSupport userTimeZoneSupport;

    @Override
    @Transactional(readOnly = true)
    public List<HealthConnectionDto> getConnections(String email) {
        UserEntity user = getUser(email);
        return healthConnectionRepository.findByUserOrderByProviderAsc(user).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HealthDailySummaryDto getDailySummary(String email, LocalDate date) {
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.HEALTH_INTEGRATION);
        UserEntity user = getUser(email);
        LocalDate summaryDate = date == null ? userTimeZoneSupport.today(user) : date;
        LocalDateTime start = summaryDate.atStartOfDay();
        LocalDateTime end = summaryDate.plusDays(1).atStartOfDay();

        List<HealthConnectionEntity> connections = healthConnectionRepository.findByUserOrderByProviderAsc(user);
        List<DeviceDataEntity> metrics = deviceDataRepository
                .findByUserAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(user, start, end);

        HealthDailySummaryDto dto = new HealthDailySummaryDto();
        dto.setSummaryDate(summaryDate);
        dto.setConnectedProviders(connections.stream()
                .filter(connection -> connection.getStatus() == HealthConnectionStatus.CONNECTED)
                .map(HealthConnectionEntity::getProvider)
                .toList());
        dto.setTotalSteps(metrics.stream().map(DeviceDataEntity::getSteps).filter(Objects::nonNull).mapToInt(Integer::intValue).sum());
        dto.setTotalCaloriesBurned(round(metrics.stream().map(DeviceDataEntity::getCaloriesBurned).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum()));
        dto.setTotalDistanceMeters(round(metrics.stream().map(DeviceDataEntity::getDistanceMeters).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum()));
        dto.setTotalSleepHours(round(metrics.stream().map(DeviceDataEntity::getSleepHours).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum()));
        dto.setAverageHeartRate(round(metrics.stream().map(DeviceDataEntity::getHeartRate).filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0.0)));
        dto.setHasHealthData(!metrics.isEmpty());
        dto.setLatestSyncAt(connections.stream()
                .map(HealthConnectionEntity::getLastSyncAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null));
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public HealthRangeSummaryDto getRangeSummary(String email, LocalDate startDate, LocalDate endDate) {
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.HEALTH_INTEGRATION);
        UserEntity user = getUser(email);
        LocalDate resolvedEnd = endDate == null ? userTimeZoneSupport.today(user) : endDate;
        LocalDate resolvedStart = startDate == null ? resolvedEnd.minusDays(6) : startDate;
        if (resolvedStart.isAfter(resolvedEnd)) {
            throw new IllegalArgumentException("Health summary startDate must be before or equal to endDate.");
        }
        if (resolvedStart.plusDays(90).isBefore(resolvedEnd)) {
            throw new IllegalArgumentException("Health summary date range cannot exceed 90 days.");
        }

        List<HealthDailySummaryDto> days = resolvedStart.datesUntil(resolvedEnd.plusDays(1))
                .map(date -> getDailySummary(email, date))
                .toList();

        HealthRangeSummaryDto dto = new HealthRangeSummaryDto();
        dto.setStartDate(resolvedStart);
        dto.setEndDate(resolvedEnd);
        dto.setDays(days);
        dto.setTotalSteps(days.stream().map(HealthDailySummaryDto::getTotalSteps).filter(Objects::nonNull).mapToInt(Integer::intValue).sum());
        dto.setTotalCaloriesBurned(round(days.stream().map(HealthDailySummaryDto::getTotalCaloriesBurned).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum()));
        dto.setTotalDistanceMeters(round(days.stream().map(HealthDailySummaryDto::getTotalDistanceMeters).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum()));
        dto.setTotalSleepHours(round(days.stream().map(HealthDailySummaryDto::getTotalSleepHours).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum()));
        dto.setAverageHeartRate(round(days.stream().map(HealthDailySummaryDto::getAverageHeartRate).filter(value -> value != null && value > 0).mapToDouble(Double::doubleValue).average().orElse(0.0)));
        dto.setHasHealthData(days.stream().anyMatch(day -> Boolean.TRUE.equals(day.getHasHealthData())));
        return dto;
    }

    @Override
    @Transactional
    public HealthConnectionDto connect(String email, HealthProvider provider, HealthConnectionRequestDto request) {
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.HEALTH_INTEGRATION);
        UserEntity user = getUser(email);
        HealthConnectionEntity connection = healthConnectionRepository.findByUserAndProvider(user, provider)
                .orElseGet(() -> {
                    HealthConnectionEntity created = new HealthConnectionEntity();
                    created.setUser(user);
                    created.setProvider(provider);
                    return created;
                });

        connection.setStatus(HealthConnectionStatus.CONNECTED);
        connection.setConnectedAt(LocalDateTime.now());
        connection.setDisconnectedAt(null);
        if (request != null) {
            connection.setProviderUserId(trimToNull(request.getProviderUserId()));
            connection.setDeviceModel(trimToNull(request.getDeviceModel()));
            connection.setAppVersion(trimToNull(request.getAppVersion()));
        }
        return toDto(healthConnectionRepository.save(connection));
    }

    @Override
    @Transactional
    public HealthConnectionDto disconnect(String email, HealthProvider provider) {
        UserEntity user = getUser(email);
        HealthConnectionEntity connection = healthConnectionRepository.findByUserAndProvider(user, provider)
                .orElseGet(() -> {
                    HealthConnectionEntity created = new HealthConnectionEntity();
                    created.setUser(user);
                    created.setProvider(provider);
                    created.setConnectedAt(null);
                    return created;
                });

        connection.setStatus(HealthConnectionStatus.DISCONNECTED);
        connection.setDisconnectedAt(LocalDateTime.now());
        return toDto(healthConnectionRepository.save(connection));
    }

    @Override
    @Transactional
    public HealthMetricSyncResponseDto syncMetric(String email, HealthProvider provider, HealthMetricSyncRequestDto request) {
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.HEALTH_INTEGRATION);
        if (request == null) {
            throw new IllegalArgumentException("Health metric payload is required.");
        }
        if (!hasAnyMetric(request)) {
            throw new IllegalArgumentException("At least one health metric value is required.");
        }
        if (request.getRecordedAt() == null) {
            throw new IllegalArgumentException("Health metric recordedAt is required.");
        }

        UserEntity user = getUser(email);
        HealthConnectionEntity connection = ensureConnected(user, provider);

        String externalId = trimToNull(request.getExternalId());
        DeviceDataEntity metric = externalId == null
                ? deviceDataRepository.findByUserAndProviderAndExternalIdIsNullAndRecordedAt(user, provider, request.getRecordedAt())
                .orElseGet(DeviceDataEntity::new)
                : deviceDataRepository.findByUserAndProviderAndExternalId(user, provider, externalId)
                .orElseGet(DeviceDataEntity::new);
        boolean inserted = metric.getId() == null;

        metric.setUser(user);
        metric.setProvider(provider);
        metric.setExternalId(externalId);
        metric.setSource(provider.name());
        metric.setSteps(request.getSteps());
        metric.setHeartRate(request.getHeartRate());
        metric.setSleepHours(request.getSleepHours());
        metric.setCaloriesBurned(request.getCaloriesBurned());
        metric.setDistanceMeters(request.getDistanceMeters());
        metric.setRecordedAt(request.getRecordedAt());

        DeviceDataEntity savedMetric = deviceDataRepository.save(metric);
        connection.setLastSyncAt(max(connection.getLastSyncAt(), request.getRecordedAt()));
        healthConnectionRepository.save(connection);

        return new HealthMetricSyncResponseDto(savedMetric.getId(), provider, inserted);
    }

    @Override
    @Transactional
    public HealthMetricBatchSyncResponseDto syncMetrics(String email, HealthProvider provider, HealthMetricBatchSyncRequestDto request) {
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.HEALTH_INTEGRATION);
        if (request == null || request.getMetrics() == null || request.getMetrics().isEmpty()) {
            throw new IllegalArgumentException("At least one health metric is required.");
        }
        if (request.getMetrics().size() > MAX_HEALTH_METRICS_PER_BATCH) {
            throw new IllegalArgumentException("Health metric batch cannot exceed 500 metrics.");
        }

        List<HealthMetricSyncResponseDto> results = request.getMetrics().stream()
                .map(metric -> syncMetric(email, provider, metric))
                .toList();
        int insertedCount = (int) results.stream().filter(HealthMetricSyncResponseDto::isInserted).count();
        LocalDateTime latestRecordedAt = request.getMetrics().stream()
                .map(HealthMetricSyncRequestDto::getRecordedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new HealthMetricBatchSyncResponseDto(
                provider,
                results.size(),
                insertedCount,
                results.size() - insertedCount,
                latestRecordedAt,
                results
        );
    }

    @Override
    @Transactional
    public HealthDataDeleteResponseDto deleteProviderData(String email, HealthProvider provider) {
        UserEntity user = getUser(email);
        long deletedCount = deviceDataRepository.deleteByUserAndProvider(user, provider);
        healthConnectionRepository.findByUserAndProvider(user, provider)
                .ifPresent(connection -> {
                    connection.setStatus(HealthConnectionStatus.REVOKED);
                    connection.setDisconnectedAt(LocalDateTime.now());
                    connection.setLastSyncAt(null);
                    healthConnectionRepository.save(connection);
                });
        return new HealthDataDeleteResponseDto(provider, deletedCount);
    }

    @Override
    @Transactional
    public HealthDataDeleteResponseDto deleteAllHealthData(String email) {
        UserEntity user = getUser(email);
        long deletedCount = deviceDataRepository.deleteByUser(user);
        healthConnectionRepository.findByUserOrderByProviderAsc(user)
                .forEach(connection -> {
                    connection.setStatus(HealthConnectionStatus.REVOKED);
                    connection.setDisconnectedAt(LocalDateTime.now());
                    connection.setLastSyncAt(null);
                    healthConnectionRepository.save(connection);
                });
        return new HealthDataDeleteResponseDto(null, deletedCount);
    }

    private HealthConnectionEntity ensureConnected(UserEntity user, HealthProvider provider) {
        HealthConnectionEntity connection = healthConnectionRepository.findByUserAndProvider(user, provider)
                .orElseThrow(() -> new IllegalArgumentException("Health provider is not connected."));
        if (connection.getStatus() != HealthConnectionStatus.CONNECTED) {
            throw new IllegalArgumentException("Health provider is not connected.");
        }
        return connection;
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private boolean hasAnyMetric(HealthMetricSyncRequestDto request) {
        return request.getSteps() != null
                || request.getHeartRate() != null
                || request.getSleepHours() != null
                || request.getCaloriesBurned() != null
                || request.getDistanceMeters() != null;
    }

    private HealthConnectionDto toDto(HealthConnectionEntity entity) {
        HealthConnectionDto dto = new HealthConnectionDto();
        dto.setId(entity.getId());
        dto.setProvider(entity.getProvider());
        dto.setStatus(entity.getStatus());
        dto.setProviderUserId(entity.getProviderUserId());
        dto.setDeviceModel(entity.getDeviceModel());
        dto.setAppVersion(entity.getAppVersion());
        dto.setConnectedAt(format(entity.getConnectedAt()));
        dto.setDisconnectedAt(format(entity.getDisconnectedAt()));
        dto.setLastSyncAt(format(entity.getLastSyncAt()));
        return dto;
    }

    private String format(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private LocalDateTime max(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    private Double round(Double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
