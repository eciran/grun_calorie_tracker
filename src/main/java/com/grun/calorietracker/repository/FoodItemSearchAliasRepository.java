package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodItemSearchAliasEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodItemSearchAliasRepository extends JpaRepository<FoodItemSearchAliasEntity, Long> {
    List<FoodItemSearchAliasEntity> findByFoodItemIdAndActiveTrueOrderByLanguageAscAliasAsc(Long foodItemId);
}