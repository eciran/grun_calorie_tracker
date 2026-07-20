package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserNutritionPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserNutritionPreferenceRepository
        extends JpaRepository<UserNutritionPreferenceEntity, Long> {

    Optional<UserNutritionPreferenceEntity> findByUser(UserEntity user);

    void deleteByUser(UserEntity user);
}
