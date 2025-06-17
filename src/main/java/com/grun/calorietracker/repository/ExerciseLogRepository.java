package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ExerciseLogsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseLogRepository extends JpaRepository<ExerciseLogsEntity, Long> {
}
