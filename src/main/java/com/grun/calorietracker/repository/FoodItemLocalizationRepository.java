package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodItemLocalizationEntity;
import com.grun.calorietracker.enums.PreferredLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FoodItemLocalizationRepository extends JpaRepository<FoodItemLocalizationEntity, Long> {
    Optional<FoodItemLocalizationEntity> findByFoodItemIdAndLanguage(Long foodItemId, PreferredLanguage language);
    Optional<FoodItemLocalizationEntity> findByFoodItemIdAndLanguageAndActiveTrue(Long foodItemId, PreferredLanguage language);

    List<FoodItemLocalizationEntity> findByFoodItemIdOrderByLanguageAsc(Long foodItemId);

    List<FoodItemLocalizationEntity> findByFoodItemIdInAndLanguageIn(
            Collection<Long> foodItemIds,
            Collection<PreferredLanguage> languages
    );

    List<FoodItemLocalizationEntity> findByFoodItemIdInAndLanguageInAndActiveTrue(
            Collection<Long> foodItemIds,
            Collection<PreferredLanguage> languages
    );
}