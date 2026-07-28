package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FastingHistoryCorrectionRequestDto;
import com.grun.calorietracker.dto.FastingHistoryRecordDto;
import com.grun.calorietracker.entity.FastingHistoryCorrectionEntity;
import com.grun.calorietracker.entity.FastingSessionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AdvancedFastingErrorCode;
import com.grun.calorietracker.enums.FastingHistoryCorrectionAction;
import com.grun.calorietracker.enums.FastingSessionStatus;
import com.grun.calorietracker.exception.AdvancedFastingException;
import com.grun.calorietracker.repository.FastingHistoryCorrectionRepository;
import com.grun.calorietracker.repository.FastingProgramOccurrenceRepository;
import com.grun.calorietracker.repository.FastingSessionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.AdvancedFastingHistoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdvancedFastingHistoryServiceImplTest {
    @Mock UserRepository userRepository;
    @Mock FastingSessionRepository sessionRepository;
    @Mock FastingProgramOccurrenceRepository occurrenceRepository;
    @Mock FastingHistoryCorrectionRepository correctionRepository;
    @Mock UserAnalyticsCacheRevisionService cacheRevisionService;

    AdvancedFastingHistoryServiceImpl service;
    UserEntity user;

    @BeforeEach
    void setUp() {
        service = new AdvancedFastingHistoryServiceImpl(userRepository, sessionRepository,
                occurrenceRepository, correctionRepository, cacheRevisionService);
        user = new UserEntity();
        user.setId(7L);
        user.setEmail("user@test.com");
        lenient().when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        lenient().when(occurrenceRepository.findByUserAndOccurrenceDate(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void create_persistsCompletedManualHistoryAndAudit() {
        FastingHistoryCorrectionRequestDto request = request("2026-07-20T19:00", "2026-07-21T11:00");
        when(sessionRepository.countOverlappingHistory(any(), isNull(), any(), any())).thenReturn(0L);
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            FastingSessionEntity saved = invocation.getArgument(0);
            saved.setId(12L);
            return saved;
        });

        FastingHistoryRecordDto result = service.create("user@test.com", request);

        assertEquals(12L, result.getSessionId());
        assertEquals(960, result.getActualMinutes());
        assertTrue(result.getManualEntry());
        ArgumentCaptor<FastingHistoryCorrectionEntity> audit = ArgumentCaptor.forClass(FastingHistoryCorrectionEntity.class);
        verify(correctionRepository).save(audit.capture());
        assertEquals(FastingHistoryCorrectionAction.CREATE, audit.getValue().getAction());
        assertEquals("USER_CORRECTION", audit.getValue().getCorrectionReason());
        assertNull(audit.getValue().getOldStartedAt());
    }

    @Test
    void create_rejectsDurationLongerThan24Hours() {
        FastingHistoryCorrectionRequestDto request = request("2026-07-20T10:00", "2026-07-21T10:01");

        AdvancedFastingException exception = assertThrows(AdvancedFastingException.class,
                () -> service.create("user@test.com", request));

        assertEquals(AdvancedFastingErrorCode.INVALID_FASTING_HISTORY_RANGE, exception.getCode());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void create_rejectsOverlappingHistory() {
        FastingHistoryCorrectionRequestDto request = request("2026-07-20T19:00", "2026-07-21T11:00");
        when(sessionRepository.countOverlappingHistory(any(), isNull(), any(), any())).thenReturn(1L);

        AdvancedFastingException exception = assertThrows(AdvancedFastingException.class,
                () -> service.create("user@test.com", request));

        assertEquals(AdvancedFastingErrorCode.FASTING_HISTORY_OVERLAP, exception.getCode());
    }

    @Test
    void correct_rejectsActiveSession() {
        FastingSessionEntity active = session(FastingSessionStatus.ACTIVE);
        when(sessionRepository.findByIdAndUserForUpdate(12L, user)).thenReturn(Optional.of(active));

        AdvancedFastingException exception = assertThrows(AdvancedFastingException.class,
                () -> service.correct("user@test.com", 12L, request("2026-07-20T20:00", "2026-07-21T10:00")));

        assertEquals(AdvancedFastingErrorCode.ACTIVE_FASTING_HISTORY_CANNOT_BE_EDITED, exception.getCode());
    }

    @Test
    void correct_updatesDurationAndCapturesOldAndNewTimestamps() {
        FastingSessionEntity existing = session(FastingSessionStatus.COMPLETED);
        when(sessionRepository.findByIdAndUserForUpdate(12L, user)).thenReturn(Optional.of(existing));
        when(sessionRepository.countOverlappingHistory(eq(user), eq(12L), any(), any())).thenReturn(0L);
        when(sessionRepository.save(existing)).thenReturn(existing);

        FastingHistoryRecordDto result = service.correct("user@test.com", 12L,
                request("2026-07-20T20:00", "2026-07-21T10:00"));

        assertEquals(840, result.getActualMinutes());
        ArgumentCaptor<FastingHistoryCorrectionEntity> audit = ArgumentCaptor.forClass(FastingHistoryCorrectionEntity.class);
        verify(correctionRepository).save(audit.capture());
        assertEquals(LocalDateTime.parse("2026-07-20T19:00"), audit.getValue().getOldStartedAt());
        assertEquals(LocalDateTime.parse("2026-07-20T20:00"), audit.getValue().getNewStartedAt());
    }

    @Test
    void archive_softDeletesCompletedSessionAndWritesAudit() {
        FastingSessionEntity existing = session(FastingSessionStatus.COMPLETED);
        when(sessionRepository.findByIdAndUserForUpdate(12L, user)).thenReturn(Optional.of(existing));
        when(sessionRepository.save(existing)).thenReturn(existing);

        service.archive("user@test.com", 12L, "USER_CORRECTION");

        assertNotNull(existing.getArchivedAt());
        verify(sessionRepository, never()).delete(any());
        ArgumentCaptor<FastingHistoryCorrectionEntity> audit = ArgumentCaptor.forClass(FastingHistoryCorrectionEntity.class);
        verify(correctionRepository).save(audit.capture());
        assertEquals(FastingHistoryCorrectionAction.ARCHIVE, audit.getValue().getAction());
    }

    private FastingSessionEntity session(FastingSessionStatus status) {
        FastingSessionEntity session = new FastingSessionEntity();
        session.setId(12L);
        session.setUser(user);
        session.setStatus(status);
        session.setFastingDate(LocalDateTime.parse("2026-07-20T19:00").toLocalDate());
        session.setStartedAt(LocalDateTime.parse("2026-07-20T19:00"));
        session.setEndedAt(LocalDateTime.parse("2026-07-21T11:00"));
        session.setActualMinutes(960);
        session.setManualEntry(true);
        return session;
    }

    private FastingHistoryCorrectionRequestDto request(String start, String end) {
        FastingHistoryCorrectionRequestDto request = new FastingHistoryCorrectionRequestDto();
        request.setStartedAt(LocalDateTime.parse(start));
        request.setEndedAt(LocalDateTime.parse(end));
        request.setCorrectionReason("USER_CORRECTION");
        request.setNote("Corrected by user");
        return request;
    }
}