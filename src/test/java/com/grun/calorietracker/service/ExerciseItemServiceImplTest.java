package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.ExerciseItemDto;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.enums.ExerciseDifficulty;
import com.grun.calorietracker.mapper.ExerciseItemMapper;
import com.grun.calorietracker.repository.ExerciseItemRepository;
import com.grun.calorietracker.service.impl.ExerciseItemServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExerciseItemServiceImplTest {

    @Mock
    private ExerciseItemRepository exerciseItemRepository;

    private ExerciseItemServiceImpl exerciseItemService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        exerciseItemService = new ExerciseItemServiceImpl(exerciseItemRepository, new ExerciseItemMapper());
    }

    @Test
    void addItem_setsCatalogDefaultsAndMapsRichExerciseFields() {
        ExerciseItemDto request = new ExerciseItemDto();
        request.setName("Goblet Squat");
        request.setMetCode("GOBLET_SQUAT");
        request.setCaloriesPerMinute(7.5);
        request.setPrimaryMuscleGroup("Quadriceps");
        request.setSecondaryMuscleGroups("Glutes, Hamstrings, Core");
        request.setEquipment("Dumbbell");
        request.setDifficulty(ExerciseDifficulty.BEGINNER);
        request.setInstructions("Hold the dumbbell close to the chest and squat under control.");
        request.setSafetyNotes("Keep the chest tall and knees aligned with toes.");
        request.setThumbnailUrl("https://cdn.grun.app/exercises/goblet-squat-thumb.jpg");
        request.setVideoUrl("https://cdn.grun.app/exercises/goblet-squat.mp4");
        request.setAnimationUrl("https://cdn.grun.app/exercises/goblet-squat.gif");

        when(exerciseItemRepository.save(any(ExerciseItemEntity.class))).thenAnswer(invocation -> {
            ExerciseItemEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return entity;
        });

        ExerciseItemDto result = exerciseItemService.addItem(request);

        assertEquals(10L, result.getId());
        assertEquals("Goblet Squat", result.getName());
        assertEquals("Quadriceps", result.getPrimaryMuscleGroup());
        assertEquals("Dumbbell", result.getEquipment());
        assertEquals(ExerciseDifficulty.BEGINNER, result.getDifficulty());
        assertTrue(result.getAiEligible());
        assertTrue(result.getActive());
        verify(exerciseItemRepository).save(any(ExerciseItemEntity.class));
    }

    @Test
    void updateItem_updatesRichExerciseFields() {
        ExerciseItemEntity existing = new ExerciseItemEntity();
        existing.setId(5L);
        existing.setName("Old Exercise");
        existing.setMetCode("OLD");
        existing.setCaloriesPerMinute(5.0);

        ExerciseItemDto request = new ExerciseItemDto();
        request.setName("Kettlebell Swing");
        request.setMetCode("KETTLEBELL_SWING");
        request.setCaloriesPerMinute(9.0);
        request.setPrimaryMuscleGroup("Glutes");
        request.setEquipment("Kettlebell");
        request.setDifficulty(ExerciseDifficulty.INTERMEDIATE);
        request.setAiEligible(false);
        request.setActive(true);

        when(exerciseItemRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(exerciseItemRepository.save(any(ExerciseItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExerciseItemDto result = exerciseItemService.updateItem(5L, request);

        assertEquals("Kettlebell Swing", result.getName());
        assertEquals("Glutes", result.getPrimaryMuscleGroup());
        assertEquals("Kettlebell", result.getEquipment());
        assertEquals(ExerciseDifficulty.INTERMEDIATE, result.getDifficulty());
        assertEquals(false, result.getAiEligible());
        assertTrue(result.getActive());
        verify(exerciseItemRepository).save(existing);
    }
}
