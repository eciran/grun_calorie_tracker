package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodLogsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodLogRepository extends JpaRepository<FoodLogsEntity, Long> {
}
