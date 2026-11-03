package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodBrandedDuplicateBatchJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodBrandedDuplicateBatchJobRepository extends JpaRepository<FoodBrandedDuplicateBatchJobEntity, Long> {
    List<FoodBrandedDuplicateBatchJobEntity> findTop20ByOrderByCreatedAtDesc();
    boolean existsByStatusIn(List<String> statuses);
    List<FoodBrandedDuplicateBatchJobEntity> findByStatusIn(List<String> statuses);
}
