package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiWorkoutPlanExerciseDto;
import com.grun.calorietracker.entity.ExerciseItemAliasEntity;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.enums.ExerciseTechniqueReviewStatus;
import com.grun.calorietracker.repository.ExerciseItemAliasRepository;
import com.grun.calorietracker.repository.ExerciseItemRepository;
import com.grun.calorietracker.repository.UnmatchedAiExerciseRepository;
import com.grun.calorietracker.service.support.ExerciseCatalogResolver;
import com.grun.calorietracker.service.support.ExerciseNameNormalizer;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExerciseCatalogResolverTest {
    @Test
    void normalizesTurkishExerciseNames() {
        assertEquals("sinav", ExerciseNameNormalizer.normalize("Şınav"));
        assertEquals("kosu bandi", ExerciseNameNormalizer.normalize("Koşu Bandı"));
    }

    @Test
    void resolvesLocalizedAliasToApprovedCatalogItem() {
        ExerciseItemRepository items = mock(ExerciseItemRepository.class);
        ExerciseItemAliasRepository aliases = mock(ExerciseItemAliasRepository.class);
        UnmatchedAiExerciseRepository unmatched = mock(UnmatchedAiExerciseRepository.class);
        ExerciseItemEntity item = eligible(7L, "Push-Up");
        ExerciseItemAliasEntity alias = new ExerciseItemAliasEntity(); alias.setExerciseItem(item);
        when(items.findFirstByNameIgnoreCase("Şınav")).thenReturn(Optional.empty());
        when(items.findByMetCode("ŞINAV")).thenReturn(Optional.empty());
        when(aliases.findFirstByNormalizedAliasAndLanguageAndActiveTrue("sinav", "tr")).thenReturn(Optional.of(alias));
        AiWorkoutPlanExerciseDto exercise = new AiWorkoutPlanExerciseDto(); exercise.setName("Şınav");

        Optional<ExerciseItemEntity> result = new ExerciseCatalogResolver(items, aliases, unmatched).resolve(exercise, "tr");

        assertEquals(7L, result.orElseThrow().getId());
        verify(unmatched, never()).recordOccurrence(any(), any(), any(), any(), any());
    }

    @Test
    void recordsUnmatchedNameWithAtomicRepositoryOperation() {
        ExerciseItemRepository items = mock(ExerciseItemRepository.class);
        ExerciseItemAliasRepository aliases = mock(ExerciseItemAliasRepository.class);
        UnmatchedAiExerciseRepository unmatched = mock(UnmatchedAiExerciseRepository.class);
        when(items.findFirstByNameIgnoreCase("Bilinmeyen Hareket")).thenReturn(Optional.empty());
        when(items.findByMetCode("BILINMEYEN HAREKET")).thenReturn(Optional.empty());
        when(aliases.findFirstByNormalizedAliasAndLanguageAndActiveTrue(anyString(), anyString())).thenReturn(Optional.empty());
        when(aliases.findFirstByNormalizedAliasAndActiveTrue(anyString())).thenReturn(Optional.empty());
        AiWorkoutPlanExerciseDto exercise = new AiWorkoutPlanExerciseDto();
        exercise.setName("Bilinmeyen Hareket"); exercise.setEquipmentUsed("Band"); exercise.setTargetMuscleGroup("Back");

        Optional<ExerciseItemEntity> result = new ExerciseCatalogResolver(items, aliases, unmatched).resolve(exercise, "TR");

        assertTrue(result.isEmpty());
        verify(unmatched).recordOccurrence("bilinmeyen hareket", "Bilinmeyen Hareket", "tr", "Band", "Back");
    }

    private ExerciseItemEntity eligible(Long id, String name) {
        ExerciseItemEntity item = new ExerciseItemEntity(); item.setId(id); item.setName(name);
        item.setActive(true); item.setAiEligible(true); item.setTechniqueReviewStatus(ExerciseTechniqueReviewStatus.APPROVED);
        return item;
    }
}
