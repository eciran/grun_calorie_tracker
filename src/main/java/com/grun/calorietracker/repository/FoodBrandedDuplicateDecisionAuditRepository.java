package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodBrandedDuplicateDecisionAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodBrandedDuplicateDecisionAuditRepository
        extends JpaRepository<FoodBrandedDuplicateDecisionAuditEntity, Long> {
}
