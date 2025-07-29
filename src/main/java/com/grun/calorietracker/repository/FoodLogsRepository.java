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

// Repository for food logs operations
@Repository
public interface FoodLogsRepository extends JpaRepository<FoodLogsEntity, Long> {
    List<FoodLogsEntity> findByUser(UserEntity user);
    List<FoodLogsEntity> findByUserAndLogDateBetween(UserEntity user, LocalDateTime start, LocalDateTime end);
    Optional<FoodLogsEntity> findByIdAndUser(Long id, UserEntity user);
//    @Query("SELECT new com.grun.calorietracker.dto.FoodLogDailyStatsDto(" +
//            "FUNCTION('DATE', f.logDate), SUM(fi.calories * f.portionSize/100), " +
//            "SUM(fi.protein * f.portionSize/100), SUM(fi.carbs * f.portionSize/100), SUM(fi.fat * f.portionSize/100)) " +
//            "FROM FoodLogsEntity f " +
//            "JOIN f.foodItem fi " +
//            "WHERE f.user = :user AND f.logDate BETWEEN :start AND :end " +
//            "GROUP BY FUNCTION('DATE', f.logDate) " +
//            "ORDER BY FUNCTION('DATE', f.logDate)")
//    List<FoodLogDailyStatsDto> getDailyStatsByUserAndDateBetween(
//            @Param("user") UserEntity user,
//            @Param("start") LocalDateTime start,
//            @Param("end") LocalDateTime end);

}

