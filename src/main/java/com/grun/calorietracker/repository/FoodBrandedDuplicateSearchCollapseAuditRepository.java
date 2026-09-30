package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodBrandedDuplicateSearchCollapseAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodBrandedDuplicateSearchCollapseAuditRepository
        extends JpaRepository<FoodBrandedDuplicateSearchCollapseAuditEntity, Long> {
}
