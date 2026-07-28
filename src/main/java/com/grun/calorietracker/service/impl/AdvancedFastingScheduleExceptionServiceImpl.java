package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.*;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.WeekFields;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdvancedFastingScheduleExceptionServiceImpl implements AdvancedFastingScheduleExceptionService {
    private final UserRepository userRepository;
    private final FastingScheduleExceptionRepository exceptionRepository;
    private final FastingScheduleExceptionAuditRepository auditRepository;
    private final FastingProgramOccurrenceRepository occurrenceRepository;
    private final FastingProgramDayRuleRepository ruleRepository;
    private final AdvancedFastingExecutionService executionService;
    private final FastingReminderDeliveryRepository reminderRepository;
    private final UserAnalyticsCacheRevisionService cacheRevisionService;
    private final UserTimeZoneSupport timeZoneSupport;

    @Override
    @Transactional
    public FastingScheduleExceptionDto put(String email, LocalDate date, FastingScheduleExceptionRequestDto request) {
        UserEntity user = user(email);
        requireFuture(user, date);
        validateShape(request);
        FastingProgramOccurrenceEntity source = occurrence(user, email, date);
        requireMutable(source);
        FastingScheduleExceptionEntity entity = exceptionRepository.findByUserAndSourceDateForUpdate(user, date).orElse(null);
        String oldValue = snapshot(entity);
        if (entity != null) restoreExisting(user, email, entity);
        else entity = new FastingScheduleExceptionEntity();

        entity.setUser(user);
        entity.setProgram(source.getProgram());
        entity.setProgramVersion(source.getProgramVersion());
        entity.setSourceDate(date);
        entity.setExceptionType(request.type());
        entity.setTargetDate(request.targetDate());
        entity.setMovedStartTime(request.plannedStartTime());
        apply(user, email, source, request);
        entity = exceptionRepository.save(entity);
        audit(user, entity, "UPSERT", oldValue, snapshot(entity));
        cacheRevisionService.bump(user.getId(), AnalyticsMutationSource.FASTING);
        return dto(entity);
    }

    @Override
    @Transactional
    public void delete(String email, LocalDate date) {
        UserEntity user = user(email);
        requireFuture(user, date);
        FastingScheduleExceptionEntity entity = exceptionRepository.findByUserAndSourceDateForUpdate(user, date)
                .orElseThrow(() -> new AdvancedFastingException(AdvancedFastingErrorCode.FASTING_SCHEDULE_EXCEPTION_NOT_FOUND, "Fasting schedule exception not found."));
        String oldValue = snapshot(entity);
        restoreExisting(user, email, entity);
        audit(user, entity, "DELETE", oldValue, null);
        exceptionRepository.delete(entity);
        cacheRevisionService.bump(user.getId(), AnalyticsMutationSource.FASTING);
    }

    private void apply(UserEntity user, String email, FastingProgramOccurrenceEntity source, FastingScheduleExceptionRequestDto request) {
        switch (request.type()) {
            case SKIP -> {
                source.setStatus(FastingOccurrenceStatus.SKIPPED);
                source.setAdherenceStatus(FastingAdherenceStatus.NOT_APPLICABLE);
                source.setSkipReason(FastingSkipReason.OTHER);
                source.setReasonNote("Schedule exception");
                saveAndClearReminders(source);
            }
            case MOVE_START_TIME -> {
                if (source.getRuleType() != FastingDayRuleType.FAST) invalid("Only FAST occurrences can move their start time.");
                LocalDateTime start = source.getOccurrenceDate().atTime(request.plannedStartTime());
                source.setPlannedStartAt(start);
                source.setPlannedEndAt(start.plusMinutes(source.getPlannedFastingMinutes()));
                source.setStatus(FastingOccurrenceStatus.PLANNED);
                source.setAdherenceStatus(FastingAdherenceStatus.PENDING);
                source.setSkipReason(null); source.setReasonNote(null);
                saveAndClearReminders(source);
            }
            case MOVE_REDUCED_DAY -> moveReducedDay(user, email, source, request.targetDate());
        }
    }

    private void moveReducedDay(UserEntity user, String email, FastingProgramOccurrenceEntity source, LocalDate targetDate) {
        if (source.getRuleType() != FastingDayRuleType.REDUCED_CALORIE) invalid("Only a reduced-calorie occurrence can be moved.");
        requireFuture(user, targetDate);
        if (!sameIsoWeek(source.getOccurrenceDate(), targetDate)) invalid("Reduced-calorie occurrences can only move within the same ISO week.");
        if (source.getOccurrenceDate().equals(targetDate)) invalid("Target date must differ from source date.");
        FastingProgramOccurrenceEntity target = occurrence(user, email, targetDate);
        requireMutable(target);
        if (!target.getProgram().getId().equals(source.getProgram().getId())) invalid("Target date must belong to the same fasting program.");
        if (target.getRuleType() == FastingDayRuleType.REDUCED_CALORIE) invalid("Target date is already a reduced-calorie day.");

        FastingDayRuleType targetType = target.getRuleType();
        Integer targetMinutes = target.getPlannedFastingMinutes();
        LocalDateTime targetStart = target.getPlannedStartAt();
        LocalDateTime targetEnd = target.getPlannedEndAt();
        source.setRuleType(targetType); source.setPlannedFastingMinutes(targetMinutes);
        source.setPlannedStartAt(rebase(targetStart, source.getOccurrenceDate()));
        source.setPlannedEndAt(rebase(targetEnd, source.getOccurrenceDate()));
        source.setPlannedCalorieTarget(null); source.setStatus(FastingOccurrenceStatus.PLANNED);
        source.setAdherenceStatus(targetType == FastingDayRuleType.FAST ? FastingAdherenceStatus.PENDING : FastingAdherenceStatus.NOT_APPLICABLE);
        source.setSkipReason(null); source.setReasonNote(null);

        target.setRuleType(FastingDayRuleType.REDUCED_CALORIE); target.setPlannedFastingMinutes(null);
        target.setPlannedStartAt(null); target.setPlannedEndAt(null);
        target.setPlannedCalorieTarget(sourceRule(source).getReducedCalorieTarget());
        target.setStatus(FastingOccurrenceStatus.PLANNED); target.setAdherenceStatus(FastingAdherenceStatus.PENDING);
        target.setSkipReason(null); target.setReasonNote(null);
        saveAndClearReminders(source); saveAndClearReminders(target);
    }

    private void restoreExisting(UserEntity user, String email, FastingScheduleExceptionEntity entity) {
        restore(occurrence(user, email, entity.getSourceDate()));
        if (entity.getTargetDate() != null) restore(occurrence(user, email, entity.getTargetDate()));
    }

    private void restore(FastingProgramOccurrenceEntity occurrence) {
        requireMutable(occurrence);
        FastingProgramDayRuleEntity rule = sourceRule(occurrence);
        occurrence.setRuleType(rule.getRuleType());
        occurrence.setPlannedFastingMinutes(rule.getFastingMinutes());
        occurrence.setPlannedCalorieTarget(rule.getReducedCalorieTarget());
        occurrence.setPlannedStartAt(rule.getRuleType() == FastingDayRuleType.FAST ? occurrence.getOccurrenceDate().atTime(rule.getPreferredStartTime()) : null);
        occurrence.setPlannedEndAt(occurrence.getPlannedStartAt() == null ? null : occurrence.getPlannedStartAt().plusMinutes(rule.getFastingMinutes()));
        occurrence.setStatus(FastingOccurrenceStatus.PLANNED);
        occurrence.setAdherenceStatus(rule.getRuleType() == FastingDayRuleType.FAST || rule.getRuleType() == FastingDayRuleType.REDUCED_CALORIE ? FastingAdherenceStatus.PENDING : FastingAdherenceStatus.NOT_APPLICABLE);
        occurrence.setSkipReason(null); occurrence.setReasonNote(null); occurrence.setActualCalories(null); occurrence.setEvaluatedAt(null);
        saveAndClearReminders(occurrence);
    }

    private void saveAndClearReminders(FastingProgramOccurrenceEntity occurrence) {
        occurrenceRepository.save(occurrence);
        reminderRepository.deleteUndeliveredForOccurrence(occurrence.getId());
    }

    private FastingProgramOccurrenceEntity occurrence(UserEntity user, String email, LocalDate date) {
        executionService.getOrCreate(email, date);
        return occurrenceRepository.findByUserAndOccurrenceDate(user, date)
                .orElseThrow(() -> new IllegalStateException("Fasting occurrence was not materialized."));
    }

    private FastingProgramDayRuleEntity sourceRule(FastingProgramOccurrenceEntity occurrence) {
        return ruleRepository.findByProgramVersionIdAndDayOfWeek(occurrence.getProgramVersion().getId(), occurrence.getOccurrenceDate().getDayOfWeek())
                .orElseThrow(() -> new IllegalStateException("Fasting program day rule is missing."));
    }

    private void requireMutable(FastingProgramOccurrenceEntity occurrence) {
        if (occurrence.getFastingSession() != null || occurrence.getStatus() == FastingOccurrenceStatus.IN_PROGRESS || occurrence.getStatus() == FastingOccurrenceStatus.COMPLETED)
            throw new AdvancedFastingException(AdvancedFastingErrorCode.FASTING_SCHEDULE_EXCEPTION_LOCKED, "A started or completed occurrence cannot be changed by a schedule exception.");
    }

    private void requireFuture(UserEntity user, LocalDate date) {
        if (date == null || !date.isAfter(timeZoneSupport.today(user))) invalid("Schedule exceptions require a future date.");
    }

    private void validateShape(FastingScheduleExceptionRequestDto request) {
        if (request == null || request.type() == null) invalid("Exception type is required.");
        if (request.type() == FastingScheduleExceptionType.MOVE_START_TIME && (request.plannedStartTime() == null || request.targetDate() != null)) invalid("MOVE_START_TIME requires plannedStartTime only.");
        if (request.type() == FastingScheduleExceptionType.MOVE_REDUCED_DAY && (request.targetDate() == null || request.plannedStartTime() != null)) invalid("MOVE_REDUCED_DAY requires targetDate only.");
        if (request.type() == FastingScheduleExceptionType.SKIP && (request.targetDate() != null || request.plannedStartTime() != null)) invalid("SKIP does not accept move fields.");
    }

    private boolean sameIsoWeek(LocalDate a, LocalDate b) { WeekFields iso = WeekFields.ISO; return a.get(iso.weekBasedYear()) == b.get(iso.weekBasedYear()) && a.get(iso.weekOfWeekBasedYear()) == b.get(iso.weekOfWeekBasedYear()); }
    private LocalDateTime rebase(LocalDateTime value, LocalDate date) { return value == null ? null : date.atTime(value.toLocalTime()); }
    private void invalid(String message) { throw new AdvancedFastingException(AdvancedFastingErrorCode.INVALID_FASTING_SCHEDULE_EXCEPTION, message); }
    private UserEntity user(String email) { return userRepository.findByEmail(email).orElseThrow(() -> new InvalidCredentialsException("Invalid credential")); }
    private String snapshot(FastingScheduleExceptionEntity e) { return e == null ? null : e.getExceptionType() + "|" + e.getSourceDate() + "|" + e.getTargetDate() + "|" + e.getMovedStartTime(); }
    private void audit(UserEntity user, FastingScheduleExceptionEntity e, String action, String oldValue, String newValue) { FastingScheduleExceptionAuditEntity a = new FastingScheduleExceptionAuditEntity(); a.setUser(user); a.setExceptionId(e.getId()); a.setSourceDate(e.getSourceDate()); a.setAction(action); a.setOldValue(oldValue); a.setNewValue(newValue); auditRepository.save(a); }
    private FastingScheduleExceptionDto dto(FastingScheduleExceptionEntity e) { return new FastingScheduleExceptionDto(e.getId(), e.getSourceDate(), e.getTargetDate(), e.getExceptionType(), e.getMovedStartTime(), e.getCreatedAt(), e.getUpdatedAt()); }
}