package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.AdvancedFastingException;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.FoodLogsService;
import com.grun.calorietracker.service.UserAnalyticsCacheRevisionService;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdvancedFastingDiaryConflictServiceImplTest {
    private final UserRepository users = mock(UserRepository.class);
    private final FastingProgramOccurrenceRepository occurrences = mock(FastingProgramOccurrenceRepository.class);
    private final FastingSessionRepository sessions = mock(FastingSessionRepository.class);
    private final FoodLogsService foodLogs = mock(FoodLogsService.class);
    private final UserAnalyticsCacheRevisionService revisions = mock(UserAnalyticsCacheRevisionService.class);
    private final AdvancedFastingDiaryConflictServiceImpl service = new AdvancedFastingDiaryConflictServiceImpl(
            users, occurrences, sessions, foodLogs, new UserTimeZoneSupport(), revisions);
    private UserEntity user;

    @BeforeEach
    void setUp() {
        reset(users, occurrences, sessions, foodLogs, revisions);
        user = new UserEntity();
        user.setId(7L);
        user.setEmail("user@grun.app");
        user.setTimeZone("Europe/Dublin");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @Test
    void evaluateReturnsNoConflictWithoutApplicableOccurrence() {
        when(occurrences.findByUserAndOccurrenceDateBetweenOrderByOccurrenceDateAsc(any(), any(), any()))
                .thenReturn(List.of());

        FastingDiaryConflictDto result = service.evaluate(
                user.getEmail(), new FastingDiaryConflictEvaluateRequestDto(at(20, 0), "DINNER"));

        assertFalse(result.conflict());
        assertTrue(result.allowedActions().isEmpty());
    }

    @Test
    void evaluateReturnsPlannedWindowActionsWithoutEndFast() {
        FastingProgramOccurrenceEntity occurrence = plannedOccurrence();
        when(occurrences.findByUserAndOccurrenceDateBetweenOrderByOccurrenceDateAsc(any(), any(), any()))
                .thenReturn(List.of(occurrence));

        FastingDiaryConflictDto result = service.evaluate(
                user.getEmail(), new FastingDiaryConflictEvaluateRequestDto(at(20, 0), "DINNER"));

        assertTrue(result.conflict());
        assertEquals(FastingDiaryWindowType.PLANNED_FAST, result.windowType());
        assertFalse(result.allowedActions().contains(FastingDiaryConflictResolution.KEEP_AND_END_FAST));
    }

    @Test
    void evaluatePrefersActualActiveWindow() {
        FastingProgramOccurrenceEntity occurrence = activeOccurrence();
        when(occurrences.findByUserAndOccurrenceDateBetweenOrderByOccurrenceDateAsc(any(), any(), any()))
                .thenReturn(List.of(occurrence));

        FastingDiaryConflictDto result = service.evaluate(
                user.getEmail(), new FastingDiaryConflictEvaluateRequestDto(at(20, 0), "DINNER"));

        assertEquals(FastingDiaryWindowType.ACTUAL_ACTIVE_FAST, result.windowType());
        assertTrue(result.allowedActions().contains(FastingDiaryConflictResolution.KEEP_AND_END_FAST));
    }

    @Test
    void cancelCreatesNoFoodLogAndMutatesNoFastingState() {
        FastingDiaryConflictResolutionDto result = service.resolve(
                user.getEmail(),
                new FastingDiaryConflictResolveRequestDto(
                        at(20, 0), 10L, 20L,
                        FastingDiaryConflictResolution.CANCEL_LOGGING, null));

        assertNull(result.foodLog());
        verifyNoInteractions(occurrences, sessions, foodLogs, revisions);
    }

    @Test
    void keepAndEndCompletesSessionAndCreatesFoodLog() {
        FastingProgramOccurrenceEntity occurrence = activeOccurrence();
        FastingSessionEntity session = occurrence.getFastingSession();
        FoodLogsDto requestLog = foodLog(at(20, 0));
        FoodLogsDto savedLog = foodLog(at(20, 0));
        savedLog.setId(99L);
        when(occurrences.findByIdAndUserForUpdate(10L, user)).thenReturn(Optional.of(occurrence));
        when(sessions.findByIdAndUserForUpdate(20L, user)).thenReturn(Optional.of(session));
        when(foodLogs.addFoodLog(requestLog, user.getEmail())).thenReturn(savedLog);

        FastingDiaryConflictResolutionDto result = service.resolve(
                user.getEmail(),
                new FastingDiaryConflictResolveRequestDto(
                        at(20, 0), 10L, 20L,
                        FastingDiaryConflictResolution.KEEP_AND_END_FAST, requestLog));

        assertEquals(99L, result.foodLog().getId());
        assertEquals(FastingSessionStatus.COMPLETED, session.getStatus());
        assertEquals(60, session.getActualMinutes());
        assertEquals(FastingOccurrenceStatus.COMPLETED, occurrence.getStatus());
        verify(sessions).save(session);
        verify(occurrences).save(occurrence);
        verify(foodLogs).addFoodLog(requestLog, user.getEmail());
    }

    @Test
    void staleSessionIdReturnsStableConflictCodeBeforeFoodMutation() {
        FastingProgramOccurrenceEntity occurrence = activeOccurrence();
        when(occurrences.findByIdAndUserForUpdate(10L, user)).thenReturn(Optional.of(occurrence));
        FoodLogsDto requestLog = foodLog(at(20, 0));

        AdvancedFastingException exception = assertThrows(
                AdvancedFastingException.class,
                () -> service.resolve(
                        user.getEmail(),
                        new FastingDiaryConflictResolveRequestDto(
                                at(20, 0), 10L, 999L,
                                FastingDiaryConflictResolution.KEEP_AND_CONTINUE_FAST, requestLog)));

        assertEquals(AdvancedFastingErrorCode.FASTING_CONFLICT_STATE_CHANGED, exception.getCode());
        verifyNoInteractions(foodLogs);
    }

    private FastingProgramOccurrenceEntity plannedOccurrence() {
        FastingProgramOccurrenceEntity occurrence = new FastingProgramOccurrenceEntity();
        occurrence.setId(10L);
        occurrence.setUser(user);
        occurrence.setRuleType(FastingDayRuleType.FAST);
        occurrence.setStatus(FastingOccurrenceStatus.PLANNED);
        occurrence.setPlannedStartAt(at(19, 0));
        occurrence.setPlannedEndAt(LocalDateTime.of(2026, 7, 29, 11, 0));
        return occurrence;
    }

    private FastingProgramOccurrenceEntity activeOccurrence() {
        FastingProgramOccurrenceEntity occurrence = plannedOccurrence();
        occurrence.setStatus(FastingOccurrenceStatus.IN_PROGRESS);
        FastingSessionEntity session = new FastingSessionEntity();
        session.setId(20L);
        session.setUser(user);
        session.setStatus(FastingSessionStatus.ACTIVE);
        session.setStartedAt(at(19, 0));
        session.setTargetMinutes(16 * 60);
        occurrence.setFastingSession(session);
        return occurrence;
    }

    private FoodLogsDto foodLog(LocalDateTime loggedAt) {
        FoodLogsDto dto = new FoodLogsDto();
        dto.setFoodItemId(5L);
        dto.setPortionSize(100.0);
        dto.setMealType("DINNER");
        dto.setLogDate(loggedAt);
        return dto;
    }

    private LocalDateTime at(int hour, int minute) {
        return LocalDate.of(2026, 7, 28).atTime(hour, minute);
    }
}