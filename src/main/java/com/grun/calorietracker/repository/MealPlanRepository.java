package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.MealPlanEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.MealPlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MealPlanRepository extends JpaRepository<MealPlanEntity, Long> {
    long countByUser(UserEntity user);
    List<MealPlanEntity> findByUserOrderByStartDateDesc(UserEntity user);
    List<MealPlanEntity> findByUserAndStatusNotOrderByStartDateDesc(UserEntity user, MealPlanStatus status);
    Optional<MealPlanEntity> findByIdAndUser(Long id, UserEntity user);
    long deleteByUser(UserEntity user);
}
