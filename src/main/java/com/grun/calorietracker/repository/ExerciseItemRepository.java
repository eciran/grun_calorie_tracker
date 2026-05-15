package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ExerciseItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// Repository for exercise types
@Repository
public interface ExerciseItemRepository extends JpaRepository<ExerciseItemEntity, Long>, JpaSpecificationExecutor<ExerciseItemEntity> {
    Optional<ExerciseItemEntity> findByMetCode(String metCode);
}
