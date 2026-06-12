package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.StepGoalEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StepGoalRepository extends JpaRepository<StepGoalEntity, Long> {
    Optional<StepGoalEntity> findByUser(UserEntity user);
    List<StepGoalEntity> findByReminderEnabledTrue();
    long deleteByUser(UserEntity user);
}
