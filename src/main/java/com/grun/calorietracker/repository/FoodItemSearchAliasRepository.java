package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodItemSearchAliasEntity;
import com.grun.calorietracker.enums.PreferredLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FoodItemSearchAliasRepository extends JpaRepository<FoodItemSearchAliasEntity, Long> {
    List<FoodItemSearchAliasEntity> findByFoodItemIdAndActiveTrueOrderByLanguageAscAliasAsc(Long foodItemId);
    List<FoodItemSearchAliasEntity> findByFoodItemIdOrderByActiveDescLanguageAscAliasAsc(Long foodItemId);
    Optional<FoodItemSearchAliasEntity> findByIdAndFoodItemId(Long id, Long foodItemId);
    Optional<FoodItemSearchAliasEntity> findByFoodItemIdAndNormalizedAliasAndLanguage(Long foodItemId, String normalizedAlias, PreferredLanguage language);
}