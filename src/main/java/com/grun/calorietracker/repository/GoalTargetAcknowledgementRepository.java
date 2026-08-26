package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.GoalTargetAcknowledgementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalTargetAcknowledgementRepository extends JpaRepository<GoalTargetAcknowledgementEntity, Long> {
    boolean existsByGoalId(Long goalId);
}
