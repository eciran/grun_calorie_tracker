package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodItemLocalizationEntity;
import com.grun.calorietracker.enums.PreferredLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FoodItemLocalizationRepository extends JpaRepository<FoodItemLocalizationEntity, Long> {
    Optional<FoodItemLocalizationEntity> findByFoodItemIdAndLanguageAndActiveTrue(Long foodItemId, PreferredLanguage language);
}