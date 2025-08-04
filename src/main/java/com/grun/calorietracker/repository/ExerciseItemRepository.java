package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ExerciseItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// Repository for exercise types
@Repository
public interface ExerciseItemRepository extends JpaRepository<ExerciseItemEntity, Long> {
    ExerciseItemEntity findByMetCode(String metCode);
}
