package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ExerciseProviderActivityMappingEntity;
import com.grun.calorietracker.enums.HealthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExerciseProviderActivityMappingRepository extends JpaRepository<ExerciseProviderActivityMappingEntity, Long> {
    Optional<ExerciseProviderActivityMappingEntity> findByProviderAndNormalizedProviderActivityTypeAndActiveTrue(
            HealthProvider provider,
            String normalizedProviderActivityType
    );
}
