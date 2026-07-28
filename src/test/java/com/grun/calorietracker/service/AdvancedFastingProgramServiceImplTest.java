package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.AdvancedFastingException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.impl.AdvancedFastingProgramServiceImpl;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.junit.jupiter.api.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdvancedFastingProgramServiceImplTest {
    private final UserRepository users = mock(UserRepository.class);
    private final FastingProgramRepository programs = mock(FastingProgramRepository.class);
    private final FastingProgramVersionRepository versions = mock(FastingProgramVersionRepository.class);
    private final FastingProgramDayRuleRepository rules = mock(FastingProgramDayRuleRepository.class);
    private final FastingProgramIdempotencyRepository idempotency = mock(FastingProgramIdempotencyRepository.class);
    private final FastingScheduleExceptionRepository exceptions = mock(FastingScheduleExceptionRepository.class);
    private final AdvancedFastingProgramServiceImpl service = new AdvancedFastingProgramServiceImpl(
            users, programs, versions, rules, idempotency, exceptions, new UserTimeZoneSupport());
    private UserEntity user;

    @BeforeEach
    void setup() {
        reset(users, programs, versions, rules, idempotency, exceptions);
        user = new UserEntity();
        user.setId(7L);
        user.setEmail("u@g.app");
        user.setTimeZone("Europe/Dublin");
        when(users.findByEmail("u@g.app")).thenReturn(Optional.of(user));
        when(users.findByEmailForUpdate("u@g.app")).thenReturn(Optional.of(user));
    }

    @Test
    void createRejectsConsecutiveFiveTwoDays() {
        FastingProgramRequestDto request = request();
        reduced(request, DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
        assertThrows(IllegalArgumentException.class, () -> service.create("u@g.app", "fasting-key-01", request));
        verifyNoInteractions(programs);
    }

    @Test
    void createRejectsDuplicateWeekday() {
        FastingProgramRequestDto request = request();
        request.getRules().get(6).setDayOfWeek(DayOfWeek.MONDAY);
        assertThrows(IllegalArgumentException.class, () -> service.create("u@g.app", "fasting-key-02", request));
    }

    @Test
    void createReplaysSameKeyAndPayloadWithoutCreatingSecondProgram() {
        AtomicReference<FastingProgramIdempotencyEntity> record = prepareCreatePersistence();
        FastingProgramRequestDto request = request();

        FastingProgramCreateResult first = service.create("u@g.app", "fasting-key-03", request);
        FastingProgramCreateResult replay = service.create("u@g.app", "fasting-key-03", request);

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(first.program().id(), replay.program().id());
        assertNotNull(record.get());
        verify(programs, times(1)).save(any(FastingProgramEntity.class));
    }

    @Test
    void createRejectsSameKeyWithDifferentPayload() {
        prepareCreatePersistence();
        service.create("u@g.app", "fasting-key-04", request());
        FastingProgramRequestDto changed = request();
        changed.setName("Different plan");

        AdvancedFastingException exception = assertThrows(
                AdvancedFastingException.class,
                () -> service.create("u@g.app", "fasting-key-04", changed));

        assertEquals(AdvancedFastingErrorCode.IDEMPOTENCY_KEY_REUSED, exception.getCode());
        verify(programs, times(1)).save(any(FastingProgramEntity.class));
    }

    @Test
    void getIsOwnerScoped() {
        when(programs.findByIdAndUserId(9L, 7L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.get("u@g.app", 9L));
    }

    @Test
    void activateIsIdempotent() {
        FastingProgramEntity program = program(3L, FastingProgramStatus.ACTIVE);
        when(programs.findAllByUserIdForUpdate(7L)).thenReturn(List.of(program));
        stubVersion(program);
        FastingProgramDto result = service.activate("u@g.app", 3L);
        assertEquals(FastingProgramStatus.ACTIVE, result.status());
        verify(programs, never()).saveAll(any());
    }

    @Test
    void pauseRejectsDraftTransition() {
        FastingProgramEntity program = program(4L, FastingProgramStatus.DRAFT);
        when(programs.findByIdAndUserId(4L, 7L)).thenReturn(Optional.of(program));

        AdvancedFastingException exception = assertThrows(
                AdvancedFastingException.class,
                () -> service.pause("u@g.app", 4L));

        assertEquals(AdvancedFastingErrorCode.INVALID_FASTING_PROGRAM_TRANSITION, exception.getCode());
    }

    @Test
    void listExcludesArchivedByDefaultAndSortsActiveFirst() {
        FastingProgramEntity archived = program(1L, FastingProgramStatus.ARCHIVED);
        FastingProgramEntity draft = program(2L, FastingProgramStatus.DRAFT);
        FastingProgramEntity active = program(3L, FastingProgramStatus.ACTIVE);
        when(programs.findAllByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(archived, draft, active));
        stubVersion(archived);
        stubVersion(draft);
        stubVersion(active);

        List<FastingProgramDto> result = service.list("u@g.app", null, false);

        assertEquals(List.of(3L, 2L), result.stream().map(FastingProgramDto::id).toList());
    }

    @Test
    void listRejectsUnknownStatusFilter() {
        AdvancedFastingException exception = assertThrows(
                AdvancedFastingException.class,
                () -> service.list("u@g.app", List.of("UNKNOWN"), false));
        assertEquals(AdvancedFastingErrorCode.INVALID_FASTING_PROGRAM_STATUS_FILTER, exception.getCode());
    }

    @Test
    void previewUsesUserTimezoneAcrossDstBoundary() {
        FastingProgramEntity program = program(3L, FastingProgramStatus.DRAFT);
        when(programs.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(program));
        FastingProgramVersionEntity version = stubVersion(program);
        FastingProgramDayRuleEntity sunday = new FastingProgramDayRuleEntity();
        sunday.setId(1L);
        sunday.setProgramVersion(version);
        sunday.setDayOfWeek(DayOfWeek.SUNDAY);
        sunday.setRuleType(FastingDayRuleType.FAST);
        sunday.setFastingMinutes(120);
        sunday.setPreferredStartTime(LocalTime.of(1, 30));
        List<FastingProgramDayRuleEntity> all = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day == DayOfWeek.SUNDAY) {
                all.add(sunday);
            } else {
                FastingProgramDayRuleEntity normal = new FastingProgramDayRuleEntity();
                normal.setDayOfWeek(day);
                normal.setRuleType(FastingDayRuleType.NORMAL);
                all.add(normal);
            }
        }
        when(rules.findAllByProgramVersionIdOrderByDayOfWeek(11L)).thenReturn(all);
        FastingProgramPreviewDto result = service.preview("u@g.app", 3L, LocalDate.of(2026, 3, 23));
        assertEquals("Europe/Dublin", result.timeZone());
        assertEquals(7, result.days().size());
        assertNotNull(result.days().get(6).startAt());
    }

    private AtomicReference<FastingProgramIdempotencyEntity> prepareCreatePersistence() {
        AtomicReference<FastingProgramIdempotencyEntity> record = new AtomicReference<>();
        when(idempotency.findByUserIdAndOperationAndIdempotencyKey(anyLong(), anyString(), anyString()))
                .thenAnswer(ignored -> Optional.ofNullable(record.get()));
        when(programs.save(any(FastingProgramEntity.class))).thenAnswer(invocation -> {
            FastingProgramEntity program = invocation.getArgument(0);
            if (program.getId() == null) {
                program.setId(50L);
                program.setVersion(0L);
                program.setCreatedAt(LocalDateTime.now());
                program.setUpdatedAt(LocalDateTime.now());
            }
            return program;
        });
        FastingProgramVersionEntity version = new FastingProgramVersionEntity();
        version.setId(11L);
        version.setVersionNumber(1);
        version.setSafetyPolicyVersion("FASTING_SAFETY_V1");
        when(versions.save(any(FastingProgramVersionEntity.class))).thenAnswer(invocation -> {
            FastingProgramVersionEntity saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });
        when(versions.findByProgramIdAndVersionNumber(50L, 1)).thenReturn(Optional.of(version));
        when(rules.findAllByProgramVersionIdOrderByDayOfWeek(11L)).thenReturn(List.of());
        when(idempotency.save(any(FastingProgramIdempotencyEntity.class))).thenAnswer(invocation -> {
            FastingProgramIdempotencyEntity saved = invocation.getArgument(0);
            record.set(saved);
            return saved;
        });
        return record;
    }

    private FastingProgramVersionEntity stubVersion(FastingProgramEntity program) {
        FastingProgramVersionEntity version = new FastingProgramVersionEntity();
        version.setId(11L);
        version.setProgram(program);
        version.setVersionNumber(1);
        version.setSafetyPolicyVersion("FASTING_SAFETY_V1");
        when(versions.findByProgramIdAndVersionNumber(program.getId(), 1)).thenReturn(Optional.of(version));
        when(rules.findAllByProgramVersionIdOrderByDayOfWeek(11L)).thenReturn(List.of());
        return version;
    }

    private FastingProgramEntity program(Long id, FastingProgramStatus status) {
        FastingProgramEntity program = new FastingProgramEntity();
        program.setId(id);
        program.setUser(user);
        program.setName("Plan");
        program.setStatus(status);
        program.setCurrentVersionNumber(1);
        program.setVersion(0L);
        program.setCreatedAt(LocalDateTime.now());
        program.setUpdatedAt(LocalDateTime.now());
        return program;
    }

    private FastingProgramRequestDto request() {
        FastingProgramRequestDto request = new FastingProgramRequestDto();
        request.setName("Plan");
        List<FastingDayRuleRequestDto> list = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            FastingDayRuleRequestDto rule = new FastingDayRuleRequestDto();
            rule.setDayOfWeek(day);
            rule.setRuleType(FastingDayRuleType.NORMAL);
            list.add(rule);
        }
        request.setRules(list);
        return request;
    }

    private void reduced(FastingProgramRequestDto request, DayOfWeek first, DayOfWeek second) {
        for (FastingDayRuleRequestDto rule : request.getRules()) {
            if (rule.getDayOfWeek() == first || rule.getDayOfWeek() == second) {
                rule.setRuleType(FastingDayRuleType.REDUCED_CALORIE);
                rule.setReducedCalorieTarget(600);
            }
        }
    }
}