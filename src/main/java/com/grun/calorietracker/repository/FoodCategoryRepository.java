package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodCategoryRepository extends JpaRepository<FoodCategoryEntity, Long> {
    List<FoodCategoryEntity> findAllByActiveTrueOrderBySortOrderAscNameEnAsc();
}
