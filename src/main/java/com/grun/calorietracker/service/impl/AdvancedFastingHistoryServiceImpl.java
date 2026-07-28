package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FastingHistoryCorrectionRequestDto;
import com.grun.calorietracker.dto.FastingHistoryRecordDto;
import com.grun.calorietracker.entity.FastingHistoryCorrectionEntity;
import com.grun.calorietracker.entity.FastingProgramOccurrenceEntity;
import com.grun.calorietracker.entity.FastingSessionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.AdvancedFastingException;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.FastingHistoryCorrectionRepository;
import com.grun.calorietracker.repository.FastingProgramOccurrenceRepository;
import com.grun.calorietracker.repository.FastingSessionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AdvancedFastingHistoryService;
import com.grun.calorietracker.service.UserAnalyticsCacheRevisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdvancedFastingHistoryServiceImpl implements AdvancedFastingHistoryService {

    private static final long MAX_HISTORY_MINUTES = 24 * 60;

    private final UserRepository userRepository;
    private final FastingSessionRepository sessionRepository;
    private final FastingProgramOccurrenceRepository occurrenceRepository;
    private final FastingHistoryCorrectionRepository correctionRepository;
    private final UserAnalyticsCacheRevisionService cacheRevisionService;

    @Override
    @Transactional
    public FastingHistoryRecordDto create(String email, FastingHistoryCorrectionRequestDto request) {
        UserEntity user = user(email);
        HistoryRange range = validate(request);
        ensureNoOverlap(user, null, range);

        FastingSessionEntity session = new FastingSessionEntity();
        session.setUser(user);
        session.setStatus(FastingSessionStatus.COMPLETED);
        session.setFastingDate(range.startedAt().toLocalDate());
        session.setStartedAt(range.startedAt());
        session.setEndedAt(range.endedAt());
        session.setActualMinutes(range.minutes());
        session.setManualEntry(true);
        session.setNote(normalizeNote(request.getNote()));
        session = sessionRepository.save(session);

        linkOccurrenceIfAvailable(user, session);
        correctionRepository.save(audit(session, user, FastingHistoryCorrectionAction.CREATE,
                normalizeReason(request.getCorrectionReason()), null, null, range.startedAt(), range.endedAt()));
        cacheRevisionService.bump(user.getId(), AnalyticsMutationSource.FASTING);
        return dto(session);
    }

    @Override
    @Transactional
    public FastingHistoryRecordDto correct(String email, Long sessionId, FastingHistoryCorrectionRequestDto request) {
        UserEntity user = user(email);
        FastingSessionEntity session = sessionRepository.findByIdAndUserForUpdate(sessionId, user)
                .orElseThrow(this::notFound);
        ensureEditable(session);
        HistoryRange range = validate(request);
        ensureNoOverlap(user, session.getId(), range);

        LocalDateTime oldStart = session.getStartedAt();
        LocalDateTime oldEnd = session.getEndedAt();
        LocalDate oldDate = session.getFastingDate();
        detachOccurrence(user, session, oldDate);

        session.setFastingDate(range.startedAt().toLocalDate());
        session.setStartedAt(range.startedAt());
        session.setEndedAt(range.endedAt());
        session.setActualMinutes(range.minutes());
        session.setTargetReached(session.getTargetMinutes() == null ? null : range.minutes() >= session.getTargetMinutes());
        session.setNote(normalizeNote(request.getNote()));
        session = sessionRepository.save(session);
        linkOccurrenceIfAvailable(user, session);

        correctionRepository.save(audit(session, user, FastingHistoryCorrectionAction.UPDATE,
                normalizeReason(request.getCorrectionReason()), oldStart, oldEnd, range.startedAt(), range.endedAt()));
        cacheRevisionService.bump(user.getId(), AnalyticsMutationSource.FASTING);
        return dto(session);
    }

    @Override
    @Transactional
    public void archive(String email, Long sessionId, String correctionReason) {
        UserEntity user = user(email);
        FastingSessionEntity session = sessionRepository.findByIdAndUserForUpdate(sessionId, user)
                .orElseThrow(this::notFound);
        ensureEditable(session);
        String reason = normalizeReason(correctionReason);
        detachOccurrence(user, session, session.getFastingDate());
        correctionRepository.save(audit(session, user, FastingHistoryCorrectionAction.ARCHIVE,
                reason, session.getStartedAt(), session.getEndedAt(), null, null));
        session.setArchivedAt(LocalDateTime.now());
        sessionRepository.save(session);
        cacheRevisionService.bump(user.getId(), AnalyticsMutationSource.FASTING);
    }

    private HistoryRange validate(FastingHistoryCorrectionRequestDto request) {
        if (request == null || request.getStartedAt() == null || request.getEndedAt() == null) {
            throw invalidRange("startedAt and endedAt are required.");
        }
        long minutes = Duration.between(request.getStartedAt(), request.getEndedAt()).toMinutes();
        if (minutes <= 0 || minutes > MAX_HISTORY_MINUTES) {
            throw invalidRange("Fasting history duration must be greater than zero and at most 24 hours.");
        }
        return new HistoryRange(request.getStartedAt(), request.getEndedAt(), Math.toIntExact(minutes));
    }

    private void ensureNoOverlap(UserEntity user, Long excludedId, HistoryRange range) {
        if (sessionRepository.countOverlappingHistory(user, excludedId, range.startedAt(), range.endedAt()) > 0) {
            throw new AdvancedFastingException(AdvancedFastingErrorCode.FASTING_HISTORY_OVERLAP,
                    "The fasting history range overlaps another session.");
        }
    }

    private void ensureEditable(FastingSessionEntity session) {
        if (session.getStatus() == FastingSessionStatus.ACTIVE) {
            throw new AdvancedFastingException(AdvancedFastingErrorCode.ACTIVE_FASTING_HISTORY_CANNOT_BE_EDITED,
                    "An active fasting session cannot be changed through history correction.");
        }
    }

    private void linkOccurrenceIfAvailable(UserEntity user, FastingSessionEntity session) {
        occurrenceRepository.findByUserAndOccurrenceDate(user, session.getFastingDate()).ifPresent(occurrence -> {
            if (occurrence.getFastingSession() == null || occurrence.getFastingSession().getId().equals(session.getId())) {
                occurrence.setFastingSession(session);
                if (session.getTargetMinutes() == null && occurrence.getPlannedFastingMinutes() != null) {
                    session.setTargetMinutes(occurrence.getPlannedFastingMinutes());
                    session.setTargetEndAt(session.getStartedAt().plusMinutes(session.getTargetMinutes()));
                    session.setTargetReached(session.getActualMinutes() >= session.getTargetMinutes());
                    copyPlannedSnapshot(occurrence, session);
                    sessionRepository.save(session);
                }
                refreshOccurrence(occurrence, session);
            }
        });
    }

    private void detachOccurrence(UserEntity user, FastingSessionEntity session, LocalDate date) {
        if (date == null) {
            return;
        }
        occurrenceRepository.findByUserAndOccurrenceDate(user, date).ifPresent(occurrence -> {
            if (occurrence.getFastingSession() != null
                    && occurrence.getFastingSession().getId().equals(session.getId())) {
                occurrence.setFastingSession(null);
                occurrence.setStatus(FastingOccurrenceStatus.PLANNED);
                occurrence.setAdherenceStatus(FastingAdherenceStatus.PENDING);
                occurrence.setEvaluatedAt(null);
                occurrenceRepository.save(occurrence);
            }
        });
    }

    private void refreshOccurrence(FastingProgramOccurrenceEntity occurrence, FastingSessionEntity session) {
        occurrence.setStatus(FastingOccurrenceStatus.COMPLETED);
        occurrence.setAdherenceStatus(session.getTargetReached() == null
                ? FastingAdherenceStatus.UNKNOWN
                : (session.getTargetReached() ? FastingAdherenceStatus.MET : FastingAdherenceStatus.NOT_MET));
        occurrence.setEvaluatedAt(LocalDateTime.now());
        occurrenceRepository.save(occurrence);
    }

    private void copyPlannedSnapshot(FastingProgramOccurrenceEntity occurrence, FastingSessionEntity session) {
        session.setPlannedRule(occurrence.getDayRule());
        session.setPlannedProgramVersion(occurrence.getProgramVersion().getVersionNumber());
        session.setPlannedRuleType(occurrence.getRuleType());
        session.setPlannedFastingMinutes(occurrence.getPlannedFastingMinutes());
        session.setPlannedReducedCalorieTarget(occurrence.getPlannedCalorieTarget());
        session.setPlannedStartAt(occurrence.getPlannedStartAt());
        session.setPlannedEndAt(occurrence.getPlannedEndAt());
    }

    private FastingHistoryCorrectionEntity audit(FastingSessionEntity session, UserEntity user,
                                                   FastingHistoryCorrectionAction action, String reason,
                                                   LocalDateTime oldStart, LocalDateTime oldEnd,
                                                   LocalDateTime newStart, LocalDateTime newEnd) {
        FastingHistoryCorrectionEntity audit = new FastingHistoryCorrectionEntity();
        audit.setSession(session);
        audit.setUser(user);
        audit.setActor(user);
        audit.setAction(action);
        audit.setCorrectionReason(reason);
        audit.setOldStartedAt(oldStart);
        audit.setOldEndedAt(oldEnd);
        audit.setNewStartedAt(newStart);
        audit.setNewEndedAt(newEnd);
        return audit;
    }

    private FastingHistoryRecordDto dto(FastingSessionEntity session) {
        FastingHistoryRecordDto dto = new FastingHistoryRecordDto();
        dto.setSessionId(session.getId());
        dto.setStartedAt(session.getStartedAt());
        dto.setEndedAt(session.getEndedAt());
        dto.setActualMinutes(session.getActualMinutes());
        dto.setTargetReached(session.getTargetReached());
        dto.setManualEntry(Boolean.TRUE.equals(session.getManualEntry()));
        dto.setNote(session.getNote());
        return dto;
    }

    private UserEntity user(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private String normalizeReason(String value) {
        if (value == null || value.isBlank()) {
            throw invalidRange("correctionReason is required.");
        }
        String normalized = value.trim().toUpperCase();
        if (normalized.length() > 64 || !normalized.matches("[A-Z0-9_]+")) {
            throw invalidRange("correctionReason must be a stable uppercase code.");
        }
        return normalized;
    }

    private String normalizeNote(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 500) {
            throw invalidRange("note cannot exceed 500 characters.");
        }
        return normalized;
    }

    private AdvancedFastingException invalidRange(String message) {
        return new AdvancedFastingException(AdvancedFastingErrorCode.INVALID_FASTING_HISTORY_RANGE, message);
    }

    private AdvancedFastingException notFound() {
        return new AdvancedFastingException(AdvancedFastingErrorCode.FASTING_HISTORY_NOT_FOUND,
                "Fasting history record was not found.");
    }

    private record HistoryRange(LocalDateTime startedAt, LocalDateTime endedAt, int minutes) {}
}