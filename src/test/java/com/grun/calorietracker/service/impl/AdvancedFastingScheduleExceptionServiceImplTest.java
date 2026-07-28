package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.AdvancedFastingException;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.*;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdvancedFastingScheduleExceptionServiceImplTest {
    UserRepository users = mock(UserRepository.class);
    FastingScheduleExceptionRepository exceptions = mock(FastingScheduleExceptionRepository.class);
    FastingScheduleExceptionAuditRepository audits = mock(FastingScheduleExceptionAuditRepository.class);
    FastingProgramOccurrenceRepository occurrences = mock(FastingProgramOccurrenceRepository.class);
    FastingProgramDayRuleRepository rules = mock(FastingProgramDayRuleRepository.class);
    AdvancedFastingExecutionService execution = mock(AdvancedFastingExecutionService.class);
    FastingReminderDeliveryRepository reminders = mock(FastingReminderDeliveryRepository.class);
    UserAnalyticsCacheRevisionService cache = mock(UserAnalyticsCacheRevisionService.class);
    AdvancedFastingScheduleExceptionServiceImpl service = new AdvancedFastingScheduleExceptionServiceImpl(
            users, exceptions, audits, occurrences, rules, execution, reminders, cache, new UserTimeZoneSupport());
    UserEntity user; FastingProgramOccurrenceEntity occurrence; LocalDate date;

    @BeforeEach void setup() {
        reset(users, exceptions, audits, occurrences, rules, execution, reminders, cache);
        date = LocalDate.of(2030, 7, 30);
        user = new UserEntity(); user.setId(1L); user.setEmail("user@grun.app"); user.setTimeZone("Europe/Dublin");
        FastingProgramEntity program = new FastingProgramEntity(); program.setId(2L); program.setUser(user);
        FastingProgramVersionEntity version = new FastingProgramVersionEntity(); version.setId(3L); version.setProgram(program); version.setVersionNumber(1);
        FastingProgramDayRuleEntity rule = new FastingProgramDayRuleEntity(); rule.setId(4L); rule.setProgramVersion(version); rule.setDayOfWeek(date.getDayOfWeek()); rule.setRuleType(FastingDayRuleType.FAST); rule.setFastingMinutes(960); rule.setPreferredStartTime(LocalTime.of(19, 0));
        occurrence = new FastingProgramOccurrenceEntity(); occurrence.setId(5L); occurrence.setUser(user); occurrence.setProgram(program); occurrence.setProgramVersion(version); occurrence.setDayRule(rule); occurrence.setOccurrenceDate(date); occurrence.setRuleType(FastingDayRuleType.FAST); occurrence.setStatus(FastingOccurrenceStatus.PLANNED); occurrence.setAdherenceStatus(FastingAdherenceStatus.PENDING); occurrence.setPlannedFastingMinutes(960); occurrence.setPlannedStartAt(date.atTime(19,0)); occurrence.setPlannedEndAt(date.plusDays(1).atTime(11,0));
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(occurrences.findByUserAndOccurrenceDate(user, date)).thenReturn(Optional.of(occurrence));
        when(exceptions.findByUserAndSourceDateForUpdate(user, date)).thenReturn(Optional.empty());
        when(exceptions.save(any())).thenAnswer(inv -> { FastingScheduleExceptionEntity e=inv.getArgument(0); e.setId(10L); return e; });
        when(occurrences.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test void skipCreatesReversibleAuditedException() {
        FastingScheduleExceptionDto result = service.put(user.getEmail(), date,
                new FastingScheduleExceptionRequestDto(FastingScheduleExceptionType.SKIP, null, null));
        assertEquals(FastingScheduleExceptionType.SKIP, result.type());
        assertEquals(FastingOccurrenceStatus.SKIPPED, occurrence.getStatus());
        assertEquals(FastingAdherenceStatus.NOT_APPLICABLE, occurrence.getAdherenceStatus());
        verify(users).findByEmailForUpdate(user.getEmail());
        verify(audits).save(any(FastingScheduleExceptionAuditEntity.class));
        verify(cache).bump(user.getId(), AnalyticsMutationSource.FASTING);
    }

    @Test void movingStartTimeKeepsDurationAndClearsPendingReminders() {
        service.put(user.getEmail(), date,
                new FastingScheduleExceptionRequestDto(FastingScheduleExceptionType.MOVE_START_TIME, null, LocalTime.of(21, 15)));
        assertEquals(date.atTime(21,15), occurrence.getPlannedStartAt());
        assertEquals(date.plusDays(1).atTime(13,15), occurrence.getPlannedEndAt());
        verify(reminders).deleteUndeliveredForOccurrence(occurrence.getId());
    }

    @Test void reducedMoveRejectsSameSourceAndTargetDate() {
        occurrence.setRuleType(FastingDayRuleType.REDUCED_CALORIE);
        occurrence.setPlannedFastingMinutes(null);
        AdvancedFastingException error = assertThrows(AdvancedFastingException.class, () -> service.put(user.getEmail(), date,
                new FastingScheduleExceptionRequestDto(FastingScheduleExceptionType.MOVE_REDUCED_DAY, date, null)));
        assertEquals(AdvancedFastingErrorCode.INVALID_FASTING_SCHEDULE_EXCEPTION, error.getCode());
    }
}