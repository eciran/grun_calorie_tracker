package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.UnmatchedAiExerciseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UnmatchedAiExerciseRepository extends JpaRepository<UnmatchedAiExerciseEntity, Long> {
    Optional<UnmatchedAiExerciseEntity> findByNormalizedNameAndStatus(String normalizedName, String status);
    Page<UnmatchedAiExerciseEntity> findByStatusOrderByOccurrenceCountDescLastSeenAtDesc(String status, Pageable pageable);
    long countByStatus(String status);
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(e.occurrenceCount), 0) from UnmatchedAiExerciseEntity e where e.status = :status")
    long sumOccurrencesByStatus(@org.springframework.data.repository.query.Param("status") String status);

    @Modifying
    @Query(value = """
            INSERT INTO unmatched_ai_exercises
                (normalized_name, display_name, language, equipment, target_muscle_group,
                 occurrence_count, first_seen_at, last_seen_at, status)
            VALUES (:normalizedName, :displayName, :language, :equipment, :targetMuscleGroup,
                    1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'OPEN')
            ON CONFLICT (normalized_name, status) DO UPDATE SET
                display_name = EXCLUDED.display_name,
                language = COALESCE(EXCLUDED.language, unmatched_ai_exercises.language),
                equipment = COALESCE(EXCLUDED.equipment, unmatched_ai_exercises.equipment),
                target_muscle_group = COALESCE(EXCLUDED.target_muscle_group, unmatched_ai_exercises.target_muscle_group),
                occurrence_count = unmatched_ai_exercises.occurrence_count + 1,
                last_seen_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    void recordOccurrence(@Param("normalizedName") String normalizedName,
                          @Param("displayName") String displayName,
                          @Param("language") String language,
                          @Param("equipment") String equipment,
                          @Param("targetMuscleGroup") String targetMuscleGroup);
}
