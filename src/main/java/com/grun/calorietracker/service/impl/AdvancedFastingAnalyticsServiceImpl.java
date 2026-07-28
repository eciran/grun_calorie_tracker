package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdvancedFastingAnalyticsDto;
import com.grun.calorietracker.entity.FastingProgramOccurrenceEntity;
import com.grun.calorietracker.entity.FastingSessionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.AdvancedFastingException;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.FastingProgramOccurrenceRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AdvancedFastingAnalyticsService;
import com.grun.calorietracker.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdvancedFastingAnalyticsServiceImpl implements AdvancedFastingAnalyticsService {

    static final int MAX_RANGE_DAYS = 366;
    static final int MINIMUM_RECOMMENDED_OCCURRENCES = 7;
    static final int ON_SCHEDULE_TOLERANCE_MINUTES = 30;

    private final UserRepository userRepository;
    private final FastingProgramOccurrenceRepository occurrenceRepository;
    private final SubscriptionService subscriptionService;

    @Override
    @Transactional(readOnly = true)
    public AdvancedFastingAnalyticsDto getAnalytics(String email, LocalDate startDate, LocalDate endDate) {
        validateRange(startDate, endDate);
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.FASTING_ADVANCED);
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.ADVANCED_ANALYTICS);
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        List<FastingProgramOccurrenceEntity> occurrences =
                occurrenceRepository.findByUserAndOccurrenceDateBetweenOrderByOccurrenceDateAsc(
                        user, startDate, endDate);
        List<FastingProgramOccurrenceEntity> evaluable = occurrences.stream().filter(this::isEvaluable).toList();
        List<FastingProgramOccurrenceEntity> scheduledFast = occurrences.stream()
                .filter(o -> o.getRuleType() == FastingDayRuleType.FAST && o.getPlannedStartAt() != null)
                .filter(o -> o.getFastingSession() != null && o.getFastingSession().getStartedAt() != null)
                .toList();
        List<FastingSessionEntity> completed = occurrences.stream()
                .map(FastingProgramOccurrenceEntity::getFastingSession)
                .filter(s -> s != null && s.getStatus() == FastingSessionStatus.COMPLETED)
                .toList();
        List<FastingSessionEntity> withDuration = completed.stream()
                .filter(s -> s.getActualMinutes() != null).toList();
        List<FastingSessionEntity> earlyStops = completed.stream().filter(this::isEarlyStop).toList();

        return new AdvancedFastingAnalyticsDto(
                startDate, endDate, Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate) + 1),
                occurrences.size(), evaluable.size(),
                evaluable.size() >= MINIMUM_RECOMMENDED_OCCURRENCES,
                MINIMUM_RECOMMENDED_OCCURRENCES,
                qualityIndicators(occurrences, evaluable, scheduledFast, withDuration),
                adherence(evaluable), scheduleConsistency(scheduledFast), averageStartDeviation(scheduledFast),
                averageDuration(withDuration), completed.isEmpty() ? null : earlyStops.size(),
                completed.isEmpty() ? null : percent(earlyStops.size(), completed.size()),
                fiveTwoAdherence(occurrences), weekdayTrends(occurrences), stopReasons(completed));
    }

    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) throw new AdvancedFastingException(AdvancedFastingErrorCode.INVALID_FASTING_ANALYTICS_RANGE, "startDate and endDate are required.");
        if (endDate.isBefore(startDate)) throw new AdvancedFastingException(AdvancedFastingErrorCode.INVALID_FASTING_ANALYTICS_RANGE, "endDate must not be before startDate.");
        if (ChronoUnit.DAYS.between(startDate, endDate) + 1 > MAX_RANGE_DAYS) {
            throw new AdvancedFastingException(AdvancedFastingErrorCode.INVALID_FASTING_ANALYTICS_RANGE, "Advanced fasting analytics range cannot exceed 366 days.");
        }
    }

    private boolean isEvaluable(FastingProgramOccurrenceEntity occurrence) {
        return occurrence.getAdherenceStatus() == FastingAdherenceStatus.MET
                || occurrence.getAdherenceStatus() == FastingAdherenceStatus.NOT_MET;
    }

    private boolean isEarlyStop(FastingSessionEntity session) {
        return session.getActualMinutes() != null && session.getPlannedFastingMinutes() != null
                && session.getActualMinutes() < session.getPlannedFastingMinutes();
    }

    private Double adherence(List<FastingProgramOccurrenceEntity> occurrences) {
        if (occurrences.isEmpty()) return null;
        long met = occurrences.stream().filter(o -> o.getAdherenceStatus() == FastingAdherenceStatus.MET).count();
        return percent(met, occurrences.size());
    }

    private Double scheduleConsistency(List<FastingProgramOccurrenceEntity> occurrences) {
        if (occurrences.isEmpty()) return null;
        long onSchedule = occurrences.stream().filter(o -> startDeviation(o) <= ON_SCHEDULE_TOLERANCE_MINUTES).count();
        return percent(onSchedule, occurrences.size());
    }

    private Double averageStartDeviation(List<FastingProgramOccurrenceEntity> occurrences) {
        return occurrences.isEmpty() ? null
                : round(occurrences.stream().mapToLong(this::startDeviation).average().orElseThrow());
    }

    private long startDeviation(FastingProgramOccurrenceEntity occurrence) {
        return Math.abs(Duration.between(occurrence.getPlannedStartAt(),
                occurrence.getFastingSession().getStartedAt()).toMinutes());
    }

    private Double averageDuration(List<FastingSessionEntity> sessions) {
        return sessions.isEmpty() ? null
                : round(sessions.stream().mapToInt(FastingSessionEntity::getActualMinutes).average().orElseThrow());
    }

    private Double fiveTwoAdherence(List<FastingProgramOccurrenceEntity> occurrences) {
        return adherence(occurrences.stream()
                .filter(o -> o.getRuleType() == FastingDayRuleType.REDUCED_CALORIE)
                .filter(this::isEvaluable).toList());
    }

    private List<AdvancedFastingAnalyticsDto.WeekdayTrend> weekdayTrends(
            List<FastingProgramOccurrenceEntity> occurrences) {
        List<AdvancedFastingAnalyticsDto.WeekdayTrend> trends = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            List<FastingProgramOccurrenceEntity> dayOccurrences = occurrences.stream()
                    .filter(o -> o.getOccurrenceDate().getDayOfWeek() == day).toList();
            if (dayOccurrences.isEmpty()) continue;
            List<FastingProgramOccurrenceEntity> evaluable = dayOccurrences.stream().filter(this::isEvaluable).toList();
            List<FastingSessionEntity> durations = dayOccurrences.stream()
                    .map(FastingProgramOccurrenceEntity::getFastingSession)
                    .filter(s -> s != null && s.getActualMinutes() != null).toList();
            trends.add(new AdvancedFastingAnalyticsDto.WeekdayTrend(
                    day, dayOccurrences.size(), evaluable.size(), adherence(evaluable), averageDuration(durations)));
        }
        return List.copyOf(trends);
    }

    private Map<FastingSessionOutcomeReason, Integer> stopReasons(List<FastingSessionEntity> sessions) {
        Map<FastingSessionOutcomeReason, Integer> reasons = new EnumMap<>(FastingSessionOutcomeReason.class);
        sessions.stream().map(FastingSessionEntity::getOutcomeReason).filter(Objects::nonNull)
                .forEach(reason -> reasons.merge(reason, 1, Integer::sum));
        return Map.copyOf(reasons);
    }

    private List<String> qualityIndicators(
            List<FastingProgramOccurrenceEntity> occurrences,
            List<FastingProgramOccurrenceEntity> evaluable,
            List<FastingProgramOccurrenceEntity> scheduledFast,
            List<FastingSessionEntity> withDuration) {
        List<String> indicators = new ArrayList<>();
        if (occurrences.isEmpty()) indicators.add("NO_OCCURRENCES");
        if (evaluable.size() < MINIMUM_RECOMMENDED_OCCURRENCES) indicators.add("LIMITED_EVALUABLE_DATA");
        if (scheduledFast.isEmpty()) indicators.add("NO_SCHEDULE_TIMING_DATA");
        if (withDuration.isEmpty()) indicators.add("NO_COMPLETED_DURATION_DATA");
        if (occurrences.stream().anyMatch(o -> o.getAdherenceStatus() == FastingAdherenceStatus.UNKNOWN)) {
            indicators.add("UNKNOWN_ADHERENCE_PRESENT");
        }
        return List.copyOf(indicators);
    }

    private Double percent(long numerator, long denominator) {
        return denominator == 0 ? null : round(numerator * 100.0 / denominator);
    }

    private Double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}