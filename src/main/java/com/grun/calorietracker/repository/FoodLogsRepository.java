package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodLogsEntity;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
    List<FoodLogsEntity> findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
            UserEntity user,
            LocalDateTime start,
            LocalDateTime end
    );
    List<FoodLogsEntity> findByUserAndMealTypeAndLogDateBetween(
            UserEntity user,
            String mealType,
            LocalDateTime start,
            LocalDateTime end
    );
    Optional<FoodLogsEntity> findByIdAndUser(Long id, UserEntity user);
    boolean existsByFoodItem(FoodItemEntity foodItem);

    @Query(value = """
            SELECT f.food_id
            FROM food_logs f
            JOIN food_items fi ON fi.id = f.food_id
            WHERE f.user_id = :userId
              AND (fi.verification_status IS NULL OR fi.verification_status <> :rejectedStatus)
            GROUP BY f.food_id
            ORDER BY MAX(f.log_date) DESC, f.food_id DESC
            """, nativeQuery = true)
    List<Long> findRecentAvailableFoodItemIds(
            @Param("userId") Long userId,
            @Param("rejectedStatus") String rejectedStatus,
            Pageable pageable
    );

    @Query(value = """
            SELECT CAST(f.log_date AS DATE) AS log_date, f.meal_type
            FROM food_logs f
            JOIN food_items fi ON fi.id = f.food_id
            WHERE f.user_id = :userId
              AND (fi.verification_status IS NULL OR fi.verification_status <> :rejectedStatus)
            GROUP BY CAST(f.log_date AS DATE), f.meal_type
            ORDER BY MAX(f.log_date) DESC, f.meal_type
            """, nativeQuery = true)
    List<Object[]> findRecentMealKeys(
            @Param("userId") Long userId,
            @Param("rejectedStatus") String rejectedStatus,
            Pageable pageable
    );

    @Modifying
    @Query("UPDATE FoodLogsEntity foodLog SET foodLog.foodItem = :targetFoodItem WHERE foodLog.foodItem.id IN :sourceFoodItemIds")
    int reassignFoodItemReferences(
            @Param("targetFoodItem") FoodItemEntity targetFoodItem,
            @Param("sourceFoodItemIds") List<Long> sourceFoodItemIds
    );

    @Query(value = """
    SELECT DATE(f.log_date) as logDate,
           SUM(COALESCE(fi.calories, 0) * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100) as calories,
           SUM(COALESCE(fi.protein, 0) * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100) as protein,
           SUM(COALESCE(fi.carbs, 0) * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100) as carbs,
           SUM(COALESCE(fi.fat, 0) * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100) as fat
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

    @Query(value = """
SELECT
    COALESCE(SUM(fi.calories * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0), 0),
    COALESCE(SUM(fi.protein * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0), 0),
    COALESCE(SUM(fi.carbs * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0), 0),
    COALESCE(SUM(fi.fat * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0), 0)
FROM food_logs f
JOIN food_items fi ON f.food_id = fi.id
WHERE f.user_id = :userId
  AND f.log_date >= :start
  AND f.log_date < :end
""", nativeQuery = true)
    List<Object[]> getSummaryTotalsByUserAndDateBetween(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}

