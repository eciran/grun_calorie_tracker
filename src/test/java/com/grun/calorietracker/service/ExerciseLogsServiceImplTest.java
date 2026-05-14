package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.ExerciseLogsDto;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.entity.ExerciseLogsEntity;
import com.grun.calorietracker.entity.UserEntity;
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
        when(exerciseLogsRepository.findByUserAndSource(user, "GOOGLE_FIT")).thenReturn(List.of(entity));
        when(exerciseLogsMapper.toDto(entity)).thenReturn(dto);

        List<ExerciseLogsDto> result = exerciseLogsService.getExerciseLogsBySource("test@test.com", "google_fit");

        assertEquals(1, result.size());
        assertEquals(5L, result.get(0).getId());
        assertEquals("GOOGLE_FIT", result.get(0).getSource());
    }
}
