package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodItemServingOptionLocalizationEntity;
import com.grun.calorietracker.enums.PreferredLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FoodItemServingOptionLocalizationRepository
        extends JpaRepository<FoodItemServingOptionLocalizationEntity, Long> {

    Optional<FoodItemServingOptionLocalizationEntity> findByServingOptionIdAndLanguageAndActiveTrue(
            Long servingOptionId,
            PreferredLanguage language
    );

    List<FoodItemServingOptionLocalizationEntity> findByServingOptionIdIn(Collection<Long> servingOptionIds);

    List<FoodItemServingOptionLocalizationEntity> findByServingOptionIdInAndLanguageInAndActiveTrue(
            Collection<Long> servingOptionIds,
            Collection<PreferredLanguage> languages
    );
}