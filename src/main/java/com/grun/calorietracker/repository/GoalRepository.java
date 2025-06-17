package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.UserGoalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<UserGoalEntity, Long> {
}
