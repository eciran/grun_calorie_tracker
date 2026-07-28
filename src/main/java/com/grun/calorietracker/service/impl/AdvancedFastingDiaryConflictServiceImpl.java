package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.AdvancedFastingDiaryConflictService;
import com.grun.calorietracker.service.FoodLogsService;
import com.grun.calorietracker.service.UserAnalyticsCacheRevisionService;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdvancedFastingDiaryConflictServiceImpl implements AdvancedFastingDiaryConflictService {
    private static final List<FastingDiaryConflictResolution> ACTUAL_ACTIONS = List.of(
            FastingDiaryConflictResolution.KEEP_AND_END_FAST,
            FastingDiaryConflictResolution.KEEP_AND_CONTINUE_FAST,
            FastingDiaryConflictResolution.CANCEL_LOGGING);
    private static final List<FastingDiaryConflictResolution> PLANNED_ACTIONS = List.of(
            FastingDiaryConflictResolution.KEEP_AND_CONTINUE_FAST,
            FastingDiaryConflictResolution.CANCEL_LOGGING);

    private final UserRepository userRepository;
    private final FastingProgramOccurrenceRepository occurrenceRepository;
    private final FastingSessionRepository sessionRepository;
    private final FoodLogsService foodLogsService;
    private final UserTimeZoneSupport timeZoneSupport;
    private final UserAnalyticsCacheRevisionService cacheRevisionService;

    @Override
    @Transactional(readOnly = true)
    public FastingDiaryConflictDto evaluate(String email, FastingDiaryConflictEvaluateRequestDto request) {
        UserEntity user = user(email);
        LocalDate date = request.loggedAt().toLocalDate();
        return occurrenceRepository.findByUserAndOccurrenceDateBetweenOrderByOccurrenceDateAsc(
                        user, date.minusDays(1), date).stream()
                .map(occurrence -> conflict(occurrence, request.loggedAt()))
                .filter(FastingDiaryConflictDto::conflict)
                .findFirst()
                .orElseGet(this::noConflict);
    }

    @Override
    @Transactional
    public FastingDiaryConflictResolutionDto resolve(
            String email,
            FastingDiaryConflictResolveRequestDto request) {
        if (request.resolution() == FastingDiaryConflictResolution.CANCEL_LOGGING) {
            return new FastingDiaryConflictResolutionDto(request.resolution(), null);
        }
        if (request.foodLog() == null) {
            throw new AdvancedFastingException(
                    AdvancedFastingErrorCode.FASTING_CONFLICT_RESOLUTION_REQUIRED,
                    "A food log is required to resolve a fasting diary conflict.");
        }
        if (!request.loggedAt().equals(request.foodLog().getLogDate())) {
            throw new AdvancedFastingException(
                    AdvancedFastingErrorCode.FASTING_CONFLICT_RESOLUTION_REQUIRED,
                    "Food log timestamp must match the evaluated fasting conflict timestamp.");
        }
        if (request.occurrenceId() == null) {
            throw stateChanged();
        }

        UserEntity user = user(email);
        FastingProgramOccurrenceEntity occurrence = occurrenceRepository
                .findByIdAndUserForUpdate(request.occurrenceId(), user)
                .orElseThrow(this::stateChanged);
        FastingDiaryConflictDto current = conflict(occurrence, request.loggedAt());
        if (!current.conflict() || !Objects.equals(current.sessionId(), request.sessionId())
                || !current.allowedActions().contains(request.resolution())) {
            throw stateChanged();
        }

        if (request.resolution() == FastingDiaryConflictResolution.KEEP_AND_END_FAST) {
            finishActiveSession(user, occurrence, request.sessionId(), request.loggedAt());
        }
        FoodLogsDto created = foodLogsService.addFoodLog(request.foodLog(), email);
        return new FastingDiaryConflictResolutionDto(request.resolution(), created);
    }

    private void finishActiveSession(UserEntity user, FastingProgramOccurrenceEntity occurrence,
                                     Long sessionId, LocalDateTime endedAt) {
        if (sessionId == null) {
            throw stateChanged();
        }
        FastingSessionEntity session = sessionRepository.findByIdAndUserForUpdate(sessionId, user)
                .orElseThrow(this::stateChanged);
        if (session.getStatus() != FastingSessionStatus.ACTIVE
                || !Objects.equals(occurrence.getFastingSession().getId(), session.getId())
                || endedAt.isBefore(session.getStartedAt())) {
            throw stateChanged();
        }
        int actualMinutes = Math.toIntExact(Duration.between(session.getStartedAt(), endedAt).toMinutes());
        boolean targetReached = session.getTargetMinutes() != null && actualMinutes >= session.getTargetMinutes();
        session.setEndedAt(endedAt);
        session.setActualMinutes(actualMinutes);
        session.setTargetReached(targetReached);
        session.setStatus(FastingSessionStatus.COMPLETED);
        session.setOutcomeReason(targetReached
                ? FastingSessionOutcomeReason.TARGET_COMPLETED
                : FastingSessionOutcomeReason.USER_ENDED);
        sessionRepository.save(session);

        occurrence.setStatus(FastingOccurrenceStatus.COMPLETED);
        occurrence.setAdherenceStatus(targetReached ? FastingAdherenceStatus.MET : FastingAdherenceStatus.NOT_MET);
        occurrence.setEvaluatedAt(timeZoneSupport.now(user));
        occurrenceRepository.save(occurrence);
        cacheRevisionService.bump(user.getId(), AnalyticsMutationSource.FASTING);
    }

    private FastingDiaryConflictDto conflict(FastingProgramOccurrenceEntity occurrence, LocalDateTime loggedAt) {
        if (occurrence.getRuleType() != FastingDayRuleType.FAST
                || occurrence.getStatus() == FastingOccurrenceStatus.SKIPPED) {
            return noConflict();
        }
        FastingSessionEntity session = occurrence.getFastingSession();
        if (session != null && session.getStatus() == FastingSessionStatus.ACTIVE
                && session.getStartedAt() != null && !loggedAt.isBefore(session.getStartedAt())) {
            return new FastingDiaryConflictDto(
                    true, occurrence.getId(), session.getId(), FastingDiaryWindowType.ACTUAL_ACTIVE_FAST,
                    occurrence.getPlannedStartAt(), occurrence.getPlannedEndAt(), ACTUAL_ACTIONS);
        }
        if (occurrence.getPlannedStartAt() != null && occurrence.getPlannedEndAt() != null
                && !loggedAt.isBefore(occurrence.getPlannedStartAt())
                && loggedAt.isBefore(occurrence.getPlannedEndAt())) {
            return new FastingDiaryConflictDto(
                    true, occurrence.getId(), session == null ? null : session.getId(),
                    FastingDiaryWindowType.PLANNED_FAST,
                    occurrence.getPlannedStartAt(), occurrence.getPlannedEndAt(), PLANNED_ACTIONS);
        }
        return noConflict();
    }

    private FastingDiaryConflictDto noConflict() {
        return new FastingDiaryConflictDto(false, null, null, null, null, null, List.of());
    }

    private AdvancedFastingException stateChanged() {
        return new AdvancedFastingException(
                AdvancedFastingErrorCode.FASTING_CONFLICT_STATE_CHANGED,
                "Fasting state changed after conflict evaluation. Evaluate the diary conflict again.");
    }

    private UserEntity user(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }
}