package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AdvancedFastingOperationsConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdvancedFastingOperationsConfigRepository
        extends JpaRepository<AdvancedFastingOperationsConfigEntity, Long> {
}