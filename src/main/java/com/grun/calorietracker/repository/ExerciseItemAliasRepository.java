package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ExerciseItemAliasEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ExerciseItemAliasRepository extends JpaRepository<ExerciseItemAliasEntity, Long> {
    Optional<ExerciseItemAliasEntity> findFirstByNormalizedAliasAndActiveTrue(String normalizedAlias);
    Optional<ExerciseItemAliasEntity> findFirstByNormalizedAliasAndLanguageAndActiveTrue(String normalizedAlias, String language);
}
