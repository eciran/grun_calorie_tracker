package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodCategoryResolutionRuleEntity;
import com.grun.calorietracker.enums.FoodCategoryMappingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodCategoryResolutionRuleRepository
        extends JpaRepository<FoodCategoryResolutionRuleEntity, Long> {
    List<FoodCategoryResolutionRuleEntity> findAllByStatus(FoodCategoryMappingStatus status);
}
