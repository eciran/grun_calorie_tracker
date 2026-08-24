package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.enums.ExerciseTechniqueReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;


import java.time.LocalDateTime;
import java.util.Optional;

// Repository for exercise types
@Repository
public interface ExerciseItemRepository extends JpaRepository<ExerciseItemEntity, Long>, JpaSpecificationExecutor<ExerciseItemEntity> {
    Optional<ExerciseItemEntity> findByMetCode(String metCode);
    Optional<ExerciseItemEntity> findFirstByNameIgnoreCase(String name);

    long countByTechniqueReviewStatus(ExerciseTechniqueReviewStatus status);
    long countByReviewDueAtBefore(LocalDateTime cutoff);

    @org.springframework.data.jpa.repository.Query("select count(e) from ExerciseItemEntity e where e.thumbnailUrl is null and e.videoUrl is null and e.animationUrl is null")
    long countMissingMedia();

    @org.springframework.data.jpa.repository.Query("select count(e) from ExerciseItemEntity e where e.sourceName is null or e.licenseName is null")
    long countMissingSourceEvidence();

    @org.springframework.data.jpa.repository.Query("select count(e) from ExerciseItemEntity e where e.sourceName is not null and (e.sourceLastRefreshedAt is null or e.sourceLastRefreshedAt < :cutoff)")
    long countStaleSourceEvidence(@org.springframework.data.repository.query.Param("cutoff") LocalDateTime cutoff);
}
