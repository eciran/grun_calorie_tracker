package com.grun.calorietracker.repository;

import com.grun.calorietracker.dto.FoodLogDailyStatsDto;
import com.grun.calorietracker.entity.FoodLogsEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FoodLogsRepository extends JpaRepository<FoodLogsEntity, Long> {
    List<FoodLogsEntity> findByUser(UserEntity user);
    List<FoodLogsEntity> findByUserAndLogDateBetween(UserEntity user, LocalDateTime start, LocalDateTime end);
    Optional<FoodLogsEntity> findByIdAndUser(Long id, UserEntity user);
    @Query(value = """
    SELECT DATE(f.log_date) as logDate,
           SUM(fi.calories * f.portion_size / 100) as calories,
           SUM(fi.protein * f.portion_size / 100) as protein,
           SUM(fi.carbs * f.portion_size / 100) as carbs,
           SUM(fi.fat * f.portion_size / 100) as fat
    FROM food_logs f
    JOIN food_items fi ON f.food_id = fi.id
    WHERE f.user_id = :userId
      AND f.log_date BETWEEN :start AND :end
    GROUP BY DATE(f.log_date)
    ORDER BY DATE(f.log_date)
    """, nativeQuery = true)
    List<Object[]> getDailyStatsByUserAndDateBetween(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}

