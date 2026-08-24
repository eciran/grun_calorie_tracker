package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiWorkoutPlanDayDto;
import com.grun.calorietracker.dto.AiWorkoutPlanDraftResponseDto;
import com.grun.calorietracker.dto.WorkoutPlanDto;
import com.grun.calorietracker.dto.WorkoutPlanScheduleSessionRequestDto;
import com.grun.calorietracker.dto.WorkoutPlanScheduleUpdateRequestDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.WorkoutPlanEntity;
import com.grun.calorietracker.enums.WorkoutPlanStatus;
import com.grun.calorietracker.enums.WorkoutSessionIntensity;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.ExerciseItemRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.repository.WorkoutPlanRepository;
import com.grun.calorietracker.service.impl.AiWorkoutPlanServiceImpl;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import com.grun.calorietracker.service.support.ExerciseCatalogResolver;
import com.grun.calorietracker.service.support.ExerciseCatalogCandidateSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiWorkoutPlanServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private UserRepository userRepository;
    private WorkoutPlanRepository workoutPlanRepository;
    private UserTimeZoneSupport userTimeZoneSupport;
    private AiWorkoutPlanServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        workoutPlanRepository = mock(WorkoutPlanRepository.class);
        userTimeZoneSupport = mock(UserTimeZoneSupport.class);
        service = new AiWorkoutPlanServiceImpl(
                new AiProperties(), List.of(), mock(AiRequestHistoryRepository.class),
                userRepository, mock(ExerciseItemRepository.class), workoutPlanRepository,
                mock(SubscriptionService.class), mock(AiCreditPricingService.class), objectMapper,
                mock(AiProviderConfigurationValidator.class), userTimeZoneSupport,
                mock(ExerciseCatalogResolver.class), mock(ExerciseCatalogCandidateSelector.class));
        user = new UserEntity();
        user.setId(7L);
        user.setEmail("user@example.com");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(userTimeZoneSupport.today(user)).thenReturn(LocalDate.of(2026, 7, 15));
    }

    @Test
    void updateScheduleStoresCompleteTrustedSchedule() throws Exception {
        WorkoutPlanEntity entity = planWithDays(2);
        when(workoutPlanRepository.findByIdAndUser(99L, user)).thenReturn(Optional.of(entity));
        when(workoutPlanRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        WorkoutPlanScheduleUpdateRequestDto request = schedule(
                session(0, LocalDate.of(2026, 7, 16), LocalTime.of(18, 0)),
                session(1, LocalDate.of(2026, 7, 18), null));

        WorkoutPlanDto result = service.updateSchedule(user.getEmail(), 99L, request);

        assertTrue(result.getScheduleReady());
        assertEquals("workout_schedule_v1", result.getScheduleVersion());
        assertEquals(LocalDate.of(2026, 7, 16), result.getPlan().getDays().get(0).getScheduledDate());
        assertEquals(LocalTime.of(18, 0), result.getPlan().getDays().get(0).getScheduledStartTime());
        assertEquals(WorkoutSessionIntensity.MODERATE,
                result.getPlan().getDays().get(1).getSessionIntensity());
        verify(workoutPlanRepository).save(entity);
    }

    @Test
    void updateScheduleRejectsArchivedPlan() throws Exception {
        WorkoutPlanEntity entity = planWithDays(1);
        entity.setActive(false);
        entity.setStatus(WorkoutPlanStatus.ARCHIVED);
        when(workoutPlanRepository.findByIdAndUser(99L, user)).thenReturn(Optional.of(entity));

        assertThrows(IllegalArgumentException.class, () -> service.updateSchedule(
                user.getEmail(), 99L, schedule(session(
                        0, LocalDate.of(2026, 7, 16), LocalTime.of(18, 0)))));

        verify(workoutPlanRepository, never()).save(any());
    }

    @Test
    void updateScheduleRejectsDuplicateDatesAndPastDates() throws Exception {
        WorkoutPlanEntity entity = planWithDays(2);
        when(workoutPlanRepository.findByIdAndUser(99L, user)).thenReturn(Optional.of(entity));

        assertThrows(IllegalArgumentException.class, () -> service.updateSchedule(
                user.getEmail(), 99L, schedule(
                        session(0, LocalDate.of(2026, 7, 16), null),
                        session(1, LocalDate.of(2026, 7, 16), null))));
        assertThrows(IllegalArgumentException.class, () -> service.updateSchedule(
                user.getEmail(), 99L, schedule(
                        session(0, LocalDate.of(2026, 7, 14), null),
                        session(1, LocalDate.of(2026, 7, 17), null))));

        verify(workoutPlanRepository, never()).save(any());
    }

    private WorkoutPlanEntity planWithDays(int count) throws Exception {
        AiWorkoutPlanDraftResponseDto payload = new AiWorkoutPlanDraftResponseDto();
        java.util.ArrayList<AiWorkoutPlanDayDto> days = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            AiWorkoutPlanDayDto day = new AiWorkoutPlanDayDto();
            day.setDayLabel("Day " + (index + 1));
            day.setEstimatedDurationMinutes(45);
            days.add(day);
        }
        payload.setDays(days);
        WorkoutPlanEntity entity = new WorkoutPlanEntity();
        entity.setId(99L);
        entity.setUser(user);
        entity.setName("Active plan");
        entity.setStatus(WorkoutPlanStatus.ACTIVE);
        entity.setActive(true);
        entity.setPlanPayload(objectMapper.writeValueAsString(payload));
        entity.setCreatedAt(java.time.LocalDateTime.now());
        return entity;
    }

    private WorkoutPlanScheduleSessionRequestDto session(
            int dayIndex, LocalDate date, LocalTime time) {
        WorkoutPlanScheduleSessionRequestDto session = new WorkoutPlanScheduleSessionRequestDto();
        session.setDayIndex(dayIndex);
        session.setScheduledDate(date);
        session.setScheduledStartTime(time);
        session.setIntensity(WorkoutSessionIntensity.MODERATE);
        return session;
    }

    private WorkoutPlanScheduleUpdateRequestDto schedule(
            WorkoutPlanScheduleSessionRequestDto... sessions) {
        WorkoutPlanScheduleUpdateRequestDto request = new WorkoutPlanScheduleUpdateRequestDto();
        request.setSessions(List.of(sessions));
        return request;
    }
}
