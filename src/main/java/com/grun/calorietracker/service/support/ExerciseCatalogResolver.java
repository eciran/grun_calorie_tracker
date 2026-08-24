package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.AiWorkoutPlanExerciseDto;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.enums.ExerciseTechniqueReviewStatus;
import com.grun.calorietracker.repository.ExerciseItemAliasRepository;
import com.grun.calorietracker.repository.ExerciseItemRepository;
import com.grun.calorietracker.repository.UnmatchedAiExerciseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service @RequiredArgsConstructor
public class ExerciseCatalogResolver {
    private final ExerciseItemRepository itemRepository;
    private final ExerciseItemAliasRepository aliasRepository;
    private final UnmatchedAiExerciseRepository unmatchedRepository;

    @Transactional
    public Optional<ExerciseItemEntity> resolve(AiWorkoutPlanExerciseDto exercise, String language) {
        if (exercise.getExerciseItemId() != null && exercise.getExerciseItemId() > 0) {
            Optional<ExerciseItemEntity> byId = itemRepository.findById(exercise.getExerciseItemId()).filter(this::eligible);
            if (byId.isPresent()) return byId;
        }
        String normalized = ExerciseNameNormalizer.normalize(exercise.getName());
        Optional<ExerciseItemEntity> resolved = itemRepository.findFirstByNameIgnoreCase(exercise.getName().trim()).filter(this::eligible);
        if (resolved.isEmpty()) {
            resolved = itemRepository.findByMetCode(exercise.getName().trim().toUpperCase(java.util.Locale.ROOT)).filter(this::eligible);
        }
        if (resolved.isEmpty()) {
            String preferredLanguage = language == null || language.isBlank() ? "und" : language.trim().toLowerCase(java.util.Locale.ROOT);
            resolved = aliasRepository.findFirstByNormalizedAliasAndLanguageAndActiveTrue(normalized, preferredLanguage)
                    .or(() -> aliasRepository.findFirstByNormalizedAliasAndLanguageAndActiveTrue(normalized, "und"))
                    .or(() -> aliasRepository.findFirstByNormalizedAliasAndActiveTrue(normalized))
                    .map(alias -> alias.getExerciseItem()).filter(this::eligible);
        }
        if (resolved.isPresent()) return resolved;
        recordUnmatched(exercise, language, normalized);
        return Optional.empty();
    }

    private boolean eligible(ExerciseItemEntity item) {
        return Boolean.TRUE.equals(item.getActive()) && Boolean.TRUE.equals(item.getAiEligible())
                && item.getTechniqueReviewStatus() == ExerciseTechniqueReviewStatus.APPROVED;
    }

    private void recordUnmatched(AiWorkoutPlanExerciseDto exercise, String language, String normalized) {
        if (normalized.isBlank()) return;
        unmatchedRepository.recordOccurrence(normalized, exercise.getName(), normalizeLanguage(language),
                exercise.getEquipmentUsed(), exercise.getTargetMuscleGroup());
    }

    private String normalizeLanguage(String language) {
        return language == null || language.isBlank() ? "und" : language.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
