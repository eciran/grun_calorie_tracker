package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.SleepGoalEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SleepGoalRepository extends JpaRepository<SleepGoalEntity, Long> {
    Optional<SleepGoalEntity> findByUser(UserEntity user);
    long deleteByUser(UserEntity user);
}
