package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodBrandedDuplicateDecisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FoodBrandedDuplicateDecisionRepository
        extends JpaRepository<FoodBrandedDuplicateDecisionEntity, Long> {
    Optional<FoodBrandedDuplicateDecisionEntity> findByBrandKeyAndNameKey(String brandKey, String nameKey);
}
