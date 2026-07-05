package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.ExerciseLogsDto;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.entity.ExerciseLogsEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.ExerciseLogMeasurementType;
import com.grun.calorietracker.exception.DuplicateExternalExerciseLogException;
import com.grun.calorietracker.mapper.ExerciseLogsMapper;
import com.grun.calorietracker.repository.ExerciseItemRepository;
import com.grun.calorietracker.repository.ExerciseLogRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.ExerciseLogsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExerciseLogsServiceImplTest {

    @Mock
    private ExerciseLogRepository exerciseLogsRepository;
    @Mock
    private ExerciseItemRepository exerciseItemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ExerciseLogsMapper exerciseLogsMapper;

    @InjectMocks
    private ExerciseLogsServiceImpl exerciseLogsService;

    private UserEntity user;
    private ExerciseItemEntity exerciseItem;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        user = new UserEntity();
        user.setId(1L);
        user.setEmail("test@test.com");

        exerciseItem = new ExerciseItemEntity();
        exerciseItem.setId(3L);
        exerciseItem.setName("Running");
        exerciseItem.setAllowedMeasurementTypes("DURATION,DISTANCE,MIXED,REPS,SETS_REPS,WEIGHT_REPS");
    }

    @Test
    void addExerciseLogFromExternal_whenUnique_savesExternalLog() {
        ExerciseLogsDto request = new ExerciseLogsDto();
        request.setExerciseItemId(3L);
        request.setDurationMinutes(45);
        request.setCaloriesBurned(420.0);
        request.setLogDate(LocalDateTime.of(2026, 5, 11, 18, 30));
        request.setSource("apple_health");
        request.setExternalId(" workout-123 ");

        ExerciseLogsDto response = new ExerciseLogsDto();
        response.setId(10L);
        response.setExerciseItemId(3L);
        response.setSource("APPLE_HEALTH");
        response.setExternalId("workout-123");

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(exerciseItemRepository.findById(3L)).thenReturn(Optional.of(exerciseItem));
        when(exerciseLogsRepository.findByUserAndSourceAndExternalId(user, "APPLE_HEALTH", "workout-123"))
                .thenReturn(Optional.empty());
        when(exerciseLogsRepository.save(any(ExerciseLogsEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(exerciseLogsMapper.toDto(any(ExerciseLogsEntity.class))).thenReturn(response);

        ExerciseLogsDto result = exerciseLogsService.addExerciseLogFromExternal(request, "test@test.com");

        assertEquals(10L, result.getId());
        assertEquals("APPLE_HEALTH", result.getSource());
        assertEquals("workout-123", result.getExternalId());
        verify(exerciseLogsRepository).save(any(ExerciseLogsEntity.class));
    }

    @Test
    void addExerciseLogFromExternal_whenDuplicate_throwsDuplicateException() {
        ExerciseLogsDto request = new ExerciseLogsDto();
        request.setExerciseItemId(3L);
        request.setSource("APPLE_HEALTH");
        request.setExternalId("workout-123");

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(exerciseItemRepository.findById(3L)).thenReturn(Optional.of(exerciseItem));
        when(exerciseLogsRepository.findByUserAndSourceAndExternalId(user, "APPLE_HEALTH", "workout-123"))
                .thenReturn(Optional.of(new ExerciseLogsEntity()));

        assertThrows(DuplicateExternalExerciseLogException.class,
                () -> exerciseLogsService.addExerciseLogFromExternal(request, "test@test.com"));
    }

    @Test
    void getExerciseLogsBySource_returnsMappedLogs() {
        ExerciseLogsEntity entity = new ExerciseLogsEntity();
        entity.setId(5L);
        entity.setUser(user);
        entity.setExerciseItem(exerciseItem);
        entity.setSource("GOOGLE_FIT");

        ExerciseLogsDto dto = new ExerciseLogsDto();
        dto.setId(5L);
        dto.setSource("GOOGLE_FIT");

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(exerciseLogsRepository.findByUserAndSource(any(UserEntity.class), any(String.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(2);
                    assertEquals(0, pageable.getPageNumber());
                    assertEquals(100, pageable.getPageSize());
                    return new PageImpl<>(List.of(entity), pageable, 1);
                });
        when(exerciseLogsMapper.toDto(entity)).thenReturn(dto);

        List<ExerciseLogsDto> result = exerciseLogsService.getExerciseLogsBySource("test@test.com", "google_fit", -1, 500);

        assertEquals(1, result.size());
        assertEquals(5L, result.get(0).getId());
        assertEquals("GOOGLE_FIT", result.get(0).getSource());
    }

    @Test
    void updateExerciseLog_updatesOwnedManualLog() {
        ExerciseLogsEntity existing = new ExerciseLogsEntity();
        existing.setId(8L);
        existing.setUser(user);
        existing.setExerciseItem(exerciseItem);
        ExerciseLogsDto request = new ExerciseLogsDto();
        request.setExerciseItemId(3L);
        request.setDurationMinutes(60);
        request.setCaloriesBurned(500.0);
        request.setLogDate(LocalDateTime.of(2026, 5, 22, 19, 0));
        ExerciseLogsDto response = new ExerciseLogsDto();
        response.setId(8L);
        response.setDurationMinutes(60);
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(exerciseLogsRepository.findByIdAndUser(8L, user)).thenReturn(Optional.of(existing));
        when(exerciseItemRepository.findById(3L)).thenReturn(Optional.of(exerciseItem));
        when(exerciseLogsRepository.save(existing)).thenReturn(existing);
        when(exerciseLogsMapper.toDto(existing)).thenReturn(response);

        ExerciseLogsDto result = exerciseLogsService.updateExerciseLog(8L, request, "test@test.com");

        assertEquals(60, result.getDurationMinutes());
        assertEquals(500.0, existing.getCaloriesBurned());
        assertEquals(request.getLogDate(), existing.getLogDate());
    }

    @Test
    void addExerciseLog_whenRepsOnly_setsRepMeasurement() {
        ExerciseLogsDto request = new ExerciseLogsDto();
        request.setExerciseItemId(3L);
        request.setReps(30);
        request.setCaloriesBurned(40.0);
        request.setLogDate(LocalDateTime.of(2026, 6, 12, 8, 0));

        ExerciseLogsDto response = new ExerciseLogsDto();
        response.setId(20L);
        response.setMeasurementType(ExerciseLogMeasurementType.REPS);
        response.setReps(30);

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(exerciseItemRepository.findById(3L)).thenReturn(Optional.of(exerciseItem));
        when(exerciseLogsRepository.save(any(ExerciseLogsEntity.class))).thenAnswer(invocation -> {
            ExerciseLogsEntity entity = invocation.getArgument(0);
            assertEquals(ExerciseLogMeasurementType.REPS, entity.getMeasurementType());
            assertEquals(30, entity.getReps());
            assertEquals("MANUAL", entity.getSource());
            return entity;
        });
        when(exerciseLogsMapper.toDto(any(ExerciseLogsEntity.class))).thenReturn(response);

        ExerciseLogsDto result = exerciseLogsService.addExerciseLog(request, "test@test.com");

        assertEquals(ExerciseLogMeasurementType.REPS, result.getMeasurementType());
        assertEquals(30, result.getReps());
    }

    @Test
    void addExerciseLog_whenNoMeasurementProvided_rejects() {
        ExerciseLogsDto request = new ExerciseLogsDto();
        request.setExerciseItemId(3L);
        request.setCaloriesBurned(40.0);
        request.setLogDate(LocalDateTime.of(2026, 6, 12, 8, 0));

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(exerciseItemRepository.findById(3L)).thenReturn(Optional.of(exerciseItem));

        assertThrows(IllegalArgumentException.class, () -> exerciseLogsService.addExerciseLog(request, "test@test.com"));
    }
    @Test
    void addExerciseLog_whenMeasurementTypeNotAllowed_rejects() {
        exerciseItem.setAllowedMeasurementTypes("DURATION,DISTANCE,MIXED");
        ExerciseLogsDto request = new ExerciseLogsDto();
        request.setExerciseItemId(3L);
        request.setMeasurementType(ExerciseLogMeasurementType.SETS_REPS);
        request.setSetCount(5);
        request.setReps(5);
        request.setCaloriesBurned(40.0);
        request.setLogDate(LocalDateTime.of(2026, 6, 12, 8, 0));

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(exerciseItemRepository.findById(3L)).thenReturn(Optional.of(exerciseItem));

        assertThrows(IllegalArgumentException.class, () -> exerciseLogsService.addExerciseLog(request, "test@test.com"));
    }


    @Test
    void getExerciseLogsByDateAndUser_whenAggregateValuesAreNull_returnsZeroTotals() {
        LocalDateTime bucket = LocalDateTime.of(2026, 7, 2, 0, 0);
        LocalDateTime start = LocalDateTime.of(2026, 7, 2, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 7, 2, 23, 59);

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(exerciseLogsRepository.findByUserAndLogDateBetween(1L, start, end, "day"))
                .thenReturn(List.<Object[]>of(new Object[]{Timestamp.valueOf(bucket), null, null}));

        List<ExerciseLogsDto> result = exerciseLogsService.getExerciseLogsByDateAndUser("test@test.com", start, end, "day");

        assertEquals(1, result.size());
        assertEquals(bucket, result.get(0).getLogDate());
        assertEquals(0, result.get(0).getDurationMinutes());
        assertEquals(0.0, result.get(0).getCaloriesBurned());
    }
    @Test
    void getExerciseLogsHistory_returnsOwnedDiaryEntries() {
        ExerciseLogsEntity entity = new ExerciseLogsEntity();
        entity.setId(12L);
        entity.setUser(user);
        ExerciseLogsDto response = new ExerciseLogsDto();
        response.setId(12L);
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 8, 0, 0);
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(exerciseLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(user, start, end))
                .thenReturn(List.of(entity));
        when(exerciseLogsMapper.toDto(entity)).thenReturn(response);

        List<ExerciseLogsDto> result = exerciseLogsService.getExerciseLogsHistory("test@test.com", start, end);

        assertEquals(1, result.size());
        assertEquals(12L, result.get(0).getId());
    }
}
