package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.WorkoutPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlanEntity, Long> {
    List<WorkoutPlanEntity> findByUserAndActiveTrueOrderByCreatedAtDesc(UserEntity user);
    Optional<WorkoutPlanEntity> findByIdAndUser(Long id, UserEntity user);
}
