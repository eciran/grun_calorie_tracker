package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.FastingDiaryContextDto;
import com.grun.calorietracker.entity.FastingProgramOccurrenceEntity;
import com.grun.calorietracker.entity.FastingSessionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FastingDayRuleType;
import com.grun.calorietracker.enums.FastingOccurrenceStatus;
import com.grun.calorietracker.enums.FastingSessionStatus;
import com.grun.calorietracker.repository.FastingProgramOccurrenceRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FastingDiaryContextResolverTest {

    private final FastingProgramOccurrenceRepository repository = mock(FastingProgramOccurrenceRepository.class);
    private final FastingDiaryContextResolver resolver = new FastingDiaryContextResolver(repository);
    private final UserEntity user = user();

    @Test
    void resolveAll_marksPlannedAndActualWindowsWithOneRepositoryQuery() {
        LocalDateTime plannedOnly = LocalDateTime.of(2026, 7, 28, 21, 0);
        LocalDateTime actualAndPlanned = LocalDateTime.of(2026, 7, 28, 22, 30);
        FastingProgramOccurrenceEntity occurrence = occurrence();
        when(repository.findByUserAndOccurrenceDateBetweenOrderByOccurrenceDateAsc(
                user, LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 29)))
                .thenReturn(List.of(occurrence));

        Map<LocalDateTime, FastingDiaryContextDto> result = resolver.resolveAll(
                user, List.of(plannedOnly, actualAndPlanned));

        assertTrue(result.get(plannedOnly).insidePlannedWindow());
        assertFalse(result.get(plannedOnly).insideActualWindow());
        assertTrue(result.get(actualAndPlanned).insidePlannedWindow());
        assertTrue(result.get(actualAndPlanned).insideActualWindow());
        assertEquals(12L, result.get(actualAndPlanned).occurrenceId());
        assertEquals(21L, result.get(actualAndPlanned).sessionId());
        verify(repository, times(1)).findByUserAndOccurrenceDateBetweenOrderByOccurrenceDateAsc(
                user, LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 29));
    }

    @Test
    void resolve_excludesSkippedOccurrenceAndReturnsStableOutsideContext() {
        FastingProgramOccurrenceEntity occurrence = occurrence();
        occurrence.setStatus(FastingOccurrenceStatus.SKIPPED);
        occurrence.setFastingSession(null);
        when(repository.findByUserAndOccurrenceDateBetweenOrderByOccurrenceDateAsc(any(), any(), any()))
                .thenReturn(List.of(occurrence));

        FastingDiaryContextDto result = resolver.resolve(user, LocalDateTime.of(2026, 7, 28, 22, 0));

        assertFalse(result.insidePlannedWindow());
        assertFalse(result.insideActualWindow());
        assertNull(result.occurrenceId());
        assertNull(result.sessionId());
    }

    @Test
    void resolve_usesCompletedActualWindowAfterSessionEnds() {
        FastingProgramOccurrenceEntity occurrence = occurrence();
        occurrence.setRuleType(FastingDayRuleType.NORMAL);
        occurrence.getFastingSession().setEndedAt(LocalDateTime.of(2026, 7, 29, 6, 0));
        occurrence.getFastingSession().setStatus(FastingSessionStatus.COMPLETED);
        when(repository.findByUserAndOccurrenceDateBetweenOrderByOccurrenceDateAsc(any(), any(), any()))
                .thenReturn(List.of(occurrence));

        FastingDiaryContextDto result = resolver.resolve(user, LocalDateTime.of(2026, 7, 29, 5, 0));

        assertFalse(result.insidePlannedWindow());
        assertTrue(result.insideActualWindow());
        assertEquals(21L, result.sessionId());
    }

    private FastingProgramOccurrenceEntity occurrence() {
        FastingSessionEntity session = new FastingSessionEntity();
        session.setId(21L);
        session.setStartedAt(LocalDateTime.of(2026, 7, 28, 22, 0));
        session.setStatus(FastingSessionStatus.ACTIVE);
        FastingProgramOccurrenceEntity occurrence = new FastingProgramOccurrenceEntity();
        occurrence.setId(12L);
        occurrence.setUser(user);
        occurrence.setOccurrenceDate(LocalDate.of(2026, 7, 28));
        occurrence.setRuleType(FastingDayRuleType.FAST);
        occurrence.setStatus(FastingOccurrenceStatus.IN_PROGRESS);
        occurrence.setPlannedStartAt(LocalDateTime.of(2026, 7, 28, 20, 0));
        occurrence.setPlannedEndAt(LocalDateTime.of(2026, 7, 29, 8, 0));
        occurrence.setFastingSession(session);
        return occurrence;
    }

    private UserEntity user() {
        UserEntity entity = new UserEntity();
        entity.setId(7L);
        entity.setEmail("fasting@test.com");
        return entity;
    }
}