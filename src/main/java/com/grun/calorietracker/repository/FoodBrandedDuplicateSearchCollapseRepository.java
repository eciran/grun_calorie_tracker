package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodBrandedDuplicateSearchCollapseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FoodBrandedDuplicateSearchCollapseRepository
        extends JpaRepository<FoodBrandedDuplicateSearchCollapseEntity, Long> {
    Optional<FoodBrandedDuplicateSearchCollapseEntity> findByDecisionId(Long decisionId);
    Optional<FoodBrandedDuplicateSearchCollapseEntity> findByBrandKeyAndNameKey(String brandKey, String nameKey);
    boolean existsByDecisionIdAndActiveTrue(Long decisionId);
}
