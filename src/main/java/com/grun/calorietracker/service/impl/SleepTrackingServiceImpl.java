package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.UserAnalyticsCacheNames;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.SleepTrackingService;
import com.grun.calorietracker.service.UserAnalyticsCacheRevisionService;
import com.grun.calorietracker.service.SubscriptionService;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import com.grun.calorietracker.service.support.UserAnalyticsCacheGateway;
import com.grun.calorietracker.service.support.UserAnalyticsCacheKeyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SleepTrackingServiceImpl implements SleepTrackingService {

    private static final int DEFAULT_TARGET_MINUTES = 480;
    private static final int MAX_SESSION_MINUTES = 2_160;
    private static final int MAX_RANGE_DAYS = 366;

    private final SleepSessionRepository sessionRepository;
    private final SleepGoalRepository goalRepository;
    private final UserRepository userRepository;
    private final HealthConnectionRepository healthConnectionRepository;
    private final SubscriptionService subscriptionService;
    private final UserTimeZoneSupport timeZoneSupport;
    private final UserAnalyticsCacheRevisionService analyticsCacheRevisionService;
    private final UserAnalyticsCacheGateway analyticsCacheGateway;
    private final UserAnalyticsCacheKeyFactory analyticsCacheKeyFactory;

    @Override
    @Transactional
    public SleepSessionDto createManual(String email, SleepSessionRequestDto request) {
        UserEntity user = requireUser(email);
        if (trimToNull(request.getExternalId()) != null) {
            throw new IllegalArgumentException("Manual sleep sessions cannot define externalId");
        }
        Instant start = request.getStartAt().toInstant();
        Instant end = request.getEndAt().toInstant();
        validateSessionTimes(start, end);
        if (sessionRepository.existsByUserAndProviderAndStartedAtAndEndedAt(
                user, HealthProvider.MANUAL, start, end)) {
            throw new IllegalArgumentException("An identical manual sleep session already exists");
        }
        SleepSessionDto result = save(user, HealthProvider.MANUAL, null, request, new SleepSessionEntity());
        analyticsCacheRevisionService.bump(user.getId(), AnalyticsMutationSource.SLEEP);
        return result;
    }

    @Override
    @Transactional
    public SleepSessionDto syncProvider(String email, HealthProvider provider, SleepSessionRequestDto request) {
        if (provider == null || provider == HealthProvider.MANUAL) {
            throw new IllegalArgumentException("A device health provider is required");
        }
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.HEALTH_INTEGRATION);
        UserEntity user = requireUser(email);
        HealthConnectionEntity connection = healthConnectionRepository.findByUserAndProvider(user, provider)
                .filter(item -> item.getStatus() == HealthConnectionStatus.CONNECTED)
                .orElseThrow(() -> new AccessDeniedException("Health provider is not connected"));
        String externalId = trimToNull(request.getExternalId());
        if (externalId == null) {
            throw new IllegalArgumentException("externalId is required for provider sleep sessions");
        }
        SleepSessionEntity entity = sessionRepository
                .findByUserAndProviderAndExternalId(user, provider, externalId)
                .orElseGet(SleepSessionEntity::new);
        SleepSessionDto result = save(user, provider, externalId, request, entity);
        connection.setLastSyncAt(LocalDateTime.now());
        healthConnectionRepository.save(connection);
        analyticsCacheRevisionService.bump(user.getId(), AnalyticsMutationSource.SLEEP);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SleepSessionDto> list(String email, LocalDate startDate, LocalDate endDate) {
        UserEntity user = requireUser(email);
        validateRange(user, startDate, endDate);
        return cached(email, UserAnalyticsCacheNames.SLEEP_SESSIONS, "sessions",
                () -> buildCachedSessions(email, startDate, endDate),
                startDate, endDate, user.getTimeZone());
    }

    private List<SleepSessionDto> buildCachedSessions(String email, LocalDate startDate, LocalDate endDate) {
        UserEntity user = requireUser(email);
        validateRange(user, startDate, endDate);
        return sessionRepository.findByUserAndSleepDateBetweenOrderByStartedAtAsc(user, startDate, endDate)
                .stream().map(entity -> toDto(entity, timeZoneSupport.zoneId(user))).toList();
    }

    @Override
    @Transactional
    public void deleteManual(String email, Long id) {
        UserEntity user = requireUser(email);
        SleepSessionEntity entity = sessionRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Sleep session not found"));
        if (entity.getProvider() != HealthProvider.MANUAL) {
            throw new AccessDeniedException("Provider sleep sessions must be removed through provider data deletion");
        }
        sessionRepository.delete(entity);
        analyticsCacheRevisionService.bump(user.getId(), AnalyticsMutationSource.SLEEP);
    }

    @Override
    @Transactional(readOnly = true)
    public SleepGoalDto getGoal(String email) {
        UserEntity user = requireUser(email);
        return goalRepository.findByUser(user).map(this::toGoalDto)
                .orElseGet(() -> SleepGoalDto.builder()
                        .targetMinutes(DEFAULT_TARGET_MINUTES)
                        .persisted(false)
                        .build());
    }

    @Override
    @Transactional
    public SleepGoalDto upsertGoal(String email, SleepGoalRequestDto request) {
        UserEntity user = requireUser(email);
        SleepGoalEntity entity = goalRepository.findByUser(user).orElseGet(SleepGoalEntity::new);
        LocalDateTime now = LocalDateTime.now();
        if (entity.getId() == null) {
            entity.setUser(user);
            entity.setCreatedAt(now);
        }
        entity.setTargetMinutes(request.getTargetMinutes());
        entity.setPreferredBedtime(request.getPreferredBedtime());
        entity.setPreferredWakeTime(request.getPreferredWakeTime());
        entity.setUpdatedAt(now);
        SleepGoalEntity saved = goalRepository.save(entity);
        analyticsCacheRevisionService.bump(user.getId(), AnalyticsMutationSource.SLEEP);
        return toGoalDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SleepDailySummaryDto dailySummary(String email, LocalDate date) {
        UserEntity user = requireUser(email);
        validateRange(user, date, date);
        return cached(email, UserAnalyticsCacheNames.SLEEP_DAILY, "daily",
                () -> buildCachedDailySummary(email, date), date, user.getTimeZone());
    }

    private SleepDailySummaryDto buildCachedDailySummary(String email, LocalDate date) {
        UserEntity user = requireUser(email);
        validateRange(user, date, date);
        List<SleepSessionEntity> sessions = sessionRepository
                .findByUserAndSleepDateBetweenOrderByStartedAtAsc(user, date, date);
        return buildDaily(date, sessions, targetMinutes(user), timeZoneSupport.zoneId(user));
    }

    @Override
    @Transactional(readOnly = true)
    public SleepWeeklySummaryDto weeklySummary(String email, LocalDate endDate) {
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.ADVANCED_ANALYTICS);
        UserEntity user = requireUser(email);
        LocalDate startDate = endDate.minusDays(6);
        validateRange(user, startDate, endDate);
        return cached(email, UserAnalyticsCacheNames.SLEEP_WEEKLY, "weekly",
                () -> buildCachedWeeklySummary(email, endDate),
                startDate, endDate, user.getTimeZone());
    }

    private SleepWeeklySummaryDto buildCachedWeeklySummary(String email, LocalDate endDate) {
        UserEntity user = requireUser(email);
        LocalDate startDate = endDate.minusDays(6);
        validateRange(user, startDate, endDate);
        int target = targetMinutes(user);
        ZoneId zone = timeZoneSupport.zoneId(user);
        Map<LocalDate, List<SleepSessionEntity>> byDate = sessionRepository
                .findByUserAndSleepDateBetweenOrderByStartedAtAsc(user, startDate, endDate)
                .stream().collect(Collectors.groupingBy(SleepSessionEntity::getSleepDate));
        List<SleepDailySummaryDto> days = startDate.datesUntil(endDate.plusDays(1))
                .map(date -> buildDaily(date, byDate.getOrDefault(date, List.of()), target, zone))
                .toList();
        List<SleepDailySummaryDto> logged = days.stream().filter(day -> day.getSessionCount() > 0).toList();
        return SleepWeeklySummaryDto.builder()
                .startDate(startDate)
                .endDate(endDate)
                .targetMinutes(target)
                .loggedDays(logged.size())
                .targetHitDays((int) days.stream().filter(SleepDailySummaryDto::isTargetReached).count())
                .averageSleepMinutesOnLoggedDays(logged.isEmpty() ? null : roundOne(
                        logged.stream().mapToInt(SleepDailySummaryDto::getTotalSleepMinutes).average().orElse(0)))
                .averageQualityScore(logged.isEmpty() ? null : roundOne(logged.stream()
                        .map(SleepDailySummaryDto::getAverageQualityScore).filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue).average().orElse(0)))
                .days(days)
                .build();
    }

    private <T> T cached(String email, String cacheName, String variant,
                         java.util.function.Supplier<T> loader, Object... dimensions) {
        var identity = analyticsCacheRevisionService.requireIdentity(email);
        String key = analyticsCacheKeyFactory.key(identity, variant, dimensions);
        return analyticsCacheGateway.get(cacheName, key, loader);
    }

    private SleepSessionDto save(
            UserEntity user,
            HealthProvider provider,
            String externalId,
            SleepSessionRequestDto request,
            SleepSessionEntity entity
    ) {
        Instant start = request.getStartAt().toInstant();
        Instant end = request.getEndAt().toInstant();
        int duration = validateSessionTimes(start, end);
        List<StageValue> stages = validateStages(request.getStages(), start, end);
        int target = targetMinutes(user);
        Quality quality = quality(duration, target, stages);
        ZoneId zone = timeZoneSupport.zoneId(user);
        LocalDateTime now = LocalDateTime.now();
        if (entity.getId() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUser(user);
        entity.setStartedAt(start);
        entity.setEndedAt(end);
        entity.setSleepDate(end.atZone(zone).toLocalDate());
        entity.setDurationMinutes(duration);
        entity.setTimeZone(zone.getId());
        entity.setProvider(provider);
        entity.setExternalId(externalId);
        entity.setQualityScore(quality.score());
        entity.setQualityConfidence(quality.confidence());
        entity.setNote(trimToNull(request.getNote()));
        entity.setUpdatedAt(now);
        entity.getStages().clear();
        stages.forEach(stage -> entity.getStages().add(toEntity(entity, stage)));
        return toDto(sessionRepository.save(entity), zone);
    }

    private int validateSessionTimes(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Sleep endAt must be after startAt");
        }
        if (end.isAfter(Instant.now().plus(5, ChronoUnit.MINUTES))) {
            throw new IllegalArgumentException("Sleep endAt cannot be in the future");
        }
        long duration = ChronoUnit.MINUTES.between(start, end);
        if (duration < 1 || duration > MAX_SESSION_MINUTES) {
            throw new IllegalArgumentException("Sleep duration must be between 1 and 2160 minutes");
        }
        return Math.toIntExact(duration);
    }

    private List<StageValue> validateStages(List<SleepStageRequestDto> requests, Instant sessionStart, Instant sessionEnd) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<StageValue> stages = requests.stream().map(request -> {
            Instant start = request.getStartAt().toInstant();
            Instant end = request.getEndAt().toInstant();
            if (start.isBefore(sessionStart) || end.isAfter(sessionEnd) || !end.isAfter(start)) {
                throw new IllegalArgumentException("Sleep stage must be a positive interval inside the session");
            }
            int duration = Math.toIntExact(ChronoUnit.MINUTES.between(start, end));
            if (duration < 1) {
                throw new IllegalArgumentException("Sleep stage duration must be at least one minute");
            }
            return new StageValue(request.getStageType(), start, end, duration);
        }).sorted(Comparator.comparing(StageValue::start)).toList();
        for (int index = 1; index < stages.size(); index++) {
            if (stages.get(index).start().isBefore(stages.get(index - 1).end())) {
                throw new IllegalArgumentException("Sleep stages cannot overlap");
            }
        }
        return stages;
    }

    private Quality quality(int duration, int target, List<StageValue> stages) {
        double ratio = duration / (double) target;
        double durationScore = ratio <= 1.0
                ? 70.0 * ratio
                : Math.max(50.0, 70.0 - Math.min(20.0, (ratio - 1.0) * 40.0));
        if (stages.isEmpty()) {
            return new Quality(clamp((int) Math.round(durationScore / 70.0 * 100.0)),
                    SleepQualityConfidence.DURATION_ONLY);
        }
        int covered = stages.stream().mapToInt(StageValue::duration).sum();
        int awake = stages.stream().filter(stage -> stage.type() == SleepStageType.AWAKE)
                .mapToInt(StageValue::duration).sum();
        double efficiency = Math.max(0.0, 1.0 - awake / (double) duration);
        int score = clamp((int) Math.round(durationScore + 30.0 * efficiency));
        SleepQualityConfidence confidence = covered >= duration * 0.8
                ? SleepQualityConfidence.FULL_STAGES
                : SleepQualityConfidence.PARTIAL_STAGES;
        return new Quality(score, confidence);
    }

    private SleepDailySummaryDto buildDaily(
            LocalDate date, List<SleepSessionEntity> sessions, int target, ZoneId zone) {
        int total = sessions.stream().mapToInt(SleepSessionEntity::getDurationMinutes).sum();
        EnumMap<SleepStageType, Integer> stageMinutes = new EnumMap<>(SleepStageType.class);
        sessions.stream().flatMap(session -> session.getStages().stream()).forEach(stage ->
                stageMinutes.merge(stage.getStageType(), stage.getDurationMinutes(), Integer::sum));
        return SleepDailySummaryDto.builder()
                .date(date)
                .targetMinutes(target)
                .totalSleepMinutes(total)
                .sessionCount(sessions.size())
                .averageQualityScore(sessions.isEmpty() ? null : roundOne(sessions.stream()
                        .mapToInt(SleepSessionEntity::getQualityScore).average().orElse(0)))
                .targetReached(total >= target)
                .stageMinutes(stageMinutes)
                .sessions(sessions.stream().map(entity -> toDto(entity, zone)).toList())
                .build();
    }

    private SleepStageEntity toEntity(SleepSessionEntity session, StageValue value) {
        SleepStageEntity entity = new SleepStageEntity();
        entity.setSession(session);
        entity.setStageType(value.type());
        entity.setStartedAt(value.start());
        entity.setEndedAt(value.end());
        entity.setDurationMinutes(value.duration());
        return entity;
    }

    private SleepSessionDto toDto(SleepSessionEntity entity, ZoneId zone) {
        return SleepSessionDto.builder()
                .id(entity.getId())
                .startAt(entity.getStartedAt().atZone(zone).toOffsetDateTime())
                .endAt(entity.getEndedAt().atZone(zone).toOffsetDateTime())
                .sleepDate(entity.getSleepDate())
                .durationMinutes(entity.getDurationMinutes())
                .timeZone(entity.getTimeZone())
                .provider(entity.getProvider())
                .externalId(entity.getExternalId())
                .qualityScore(entity.getQualityScore())
                .qualityConfidence(entity.getQualityConfidence())
                .note(entity.getNote())
                .stages(entity.getStages().stream().map(stage -> SleepStageDto.builder()
                        .stageType(stage.getStageType())
                        .startAt(stage.getStartedAt().atZone(zone).toOffsetDateTime())
                        .endAt(stage.getEndedAt().atZone(zone).toOffsetDateTime())
                        .durationMinutes(stage.getDurationMinutes())
                        .build()).toList())
                .build();
    }

    private SleepGoalDto toGoalDto(SleepGoalEntity entity) {
        return SleepGoalDto.builder()
                .targetMinutes(entity.getTargetMinutes())
                .preferredBedtime(entity.getPreferredBedtime())
                .preferredWakeTime(entity.getPreferredWakeTime())
                .persisted(true)
                .build();
    }

    private int targetMinutes(UserEntity user) {
        return goalRepository.findByUser(user).map(SleepGoalEntity::getTargetMinutes)
                .orElse(DEFAULT_TARGET_MINUTES);
    }

    private void validateRange(UserEntity user, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Sleep date range is invalid");
        }
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Sleep date range cannot exceed 366 days");
        }
        if (endDate.isAfter(timeZoneSupport.today(user))) {
            throw new IllegalArgumentException("Sleep date range cannot end in the future");
        }
    }

    private UserEntity requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private Double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private record StageValue(SleepStageType type, Instant start, Instant end, int duration) {}
    private record Quality(int score, SleepQualityConfidence confidence) {}
}
