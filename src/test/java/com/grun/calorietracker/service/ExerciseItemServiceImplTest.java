package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.ExerciseItemDto;
import com.grun.calorietracker.dto.ExerciseItemPageDto;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.enums.ExerciseDifficulty;
import com.grun.calorietracker.exception.DuplicateExerciseItemException;
import com.grun.calorietracker.mapper.ExerciseItemMapper;
import com.grun.calorietracker.repository.ExerciseItemRepository;
import com.grun.calorietracker.service.impl.ExerciseItemServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

        when(exerciseItemRepository.findByMetCode("GOBLET_SQUAT")).thenReturn(Optional.empty());
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
    void searchItems_returnsPaginatedExerciseCatalog() {
        ExerciseItemEntity running = new ExerciseItemEntity();
        running.setId(1L);
        running.setName("Running");
        running.setMetCode("RUNNING_GENERAL");
        running.setCaloriesPerMinute(10.5);
        running.setDifficulty(ExerciseDifficulty.INTERMEDIATE);
        running.setActive(true);

        when(exerciseItemRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(running)));

        ExerciseItemPageDto result = exerciseItemService.searchItems(
                "run",
                "Lower Body",
                "None",
                ExerciseDifficulty.INTERMEDIATE,
                true,
                0,
                25
        );

        assertEquals(1, result.getContent().size());
        assertEquals("Running", result.getContent().get(0).getName());
        assertEquals(0, result.getPage());
        assertEquals(1L, result.getTotalElements());
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
        when(exerciseItemRepository.findByMetCode("KETTLEBELL_SWING")).thenReturn(Optional.empty());
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

    @Test
    void addItem_whenMetCodeExists_throwsDuplicateExerciseItemException() {
        ExerciseItemDto request = new ExerciseItemDto();
        request.setName("Running");
        request.setMetCode("running_general");
        request.setCaloriesPerMinute(10.5);

        ExerciseItemEntity existing = new ExerciseItemEntity();
        existing.setId(1L);
        existing.setMetCode("RUNNING_GENERAL");

        when(exerciseItemRepository.findByMetCode("RUNNING_GENERAL")).thenReturn(Optional.of(existing));

        assertThrows(DuplicateExerciseItemException.class, () -> exerciseItemService.addItem(request));
        verify(exerciseItemRepository, never()).save(any(ExerciseItemEntity.class));
    }

    @Test
    void updateItem_whenMetCodeBelongsToAnotherItem_throwsDuplicateExerciseItemException() {
        ExerciseItemEntity current = new ExerciseItemEntity();
        current.setId(5L);
        current.setMetCode("OLD_CODE");

        ExerciseItemEntity duplicate = new ExerciseItemEntity();
        duplicate.setId(7L);
        duplicate.setMetCode("RUNNING_GENERAL");

        ExerciseItemDto request = new ExerciseItemDto();
        request.setName("Running");
        request.setMetCode("RUNNING_GENERAL");
        request.setCaloriesPerMinute(10.5);

        when(exerciseItemRepository.findById(5L)).thenReturn(Optional.of(current));
        when(exerciseItemRepository.findByMetCode("RUNNING_GENERAL")).thenReturn(Optional.of(duplicate));

        assertThrows(DuplicateExerciseItemException.class, () -> exerciseItemService.updateItem(5L, request));
        verify(exerciseItemRepository, never()).save(any(ExerciseItemEntity.class));
    }
}
