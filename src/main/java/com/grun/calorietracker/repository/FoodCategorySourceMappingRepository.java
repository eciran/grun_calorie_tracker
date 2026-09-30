package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodCategorySourceMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodCategorySourceMappingRepository extends JpaRepository<FoodCategorySourceMappingEntity, Long> {
    List<FoodCategorySourceMappingEntity> findAllByOrderByPrimaryPriorityAscIdAsc();
}
