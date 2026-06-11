package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodLogsEntity;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.domain.Page;
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
    Page<FoodLogsEntity> findByUserOrderByLogDateDesc(UserEntity user, Pageable pageable);
    long countByUser(UserEntity user);
    Optional<FoodLogsEntity> findTopByUserOrderByLogDateDesc(UserEntity user);
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
            SELECT COUNT(DISTINCT CAST(f.log_date AS DATE))
            FROM food_logs f
            WHERE f.user_id = :userId
            """, nativeQuery = true)
    long countDistinctLogDaysByUserId(@Param("userId") Long userId);

    @Query(value = """
            SELECT COALESCE(MAX(meal_count), 0)
            FROM (
                SELECT CAST(f.log_date AS DATE) AS log_day, COUNT(DISTINCT UPPER(f.meal_type)) AS meal_count
                FROM food_logs f
                WHERE f.user_id = :userId
                  AND UPPER(f.meal_type) IN ('BREAKFAST', 'LUNCH', 'DINNER')
                GROUP BY CAST(f.log_date AS DATE)
            ) daily_meals
            """, nativeQuery = true)
    int maxCoreMealTypesLoggedInSingleDay(@Param("userId") Long userId);

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
    SELECT CAST(f.log_date AS DATE) as logDate,
           SUM(COALESCE(f.snapshot_calories, COALESCE(fi.calories, 0) * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100)) as calories,
           SUM(COALESCE(f.snapshot_protein, COALESCE(fi.protein, 0) * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100)) as protein,
           SUM(COALESCE(f.snapshot_carbs, COALESCE(fi.carbs, 0) * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100)) as carbs,
           SUM(COALESCE(f.snapshot_fat, COALESCE(fi.fat, 0) * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100)) as fat,
           SUM(f.snapshot_fiber) as fiber,
           SUM(f.snapshot_sugar) as sugar,
           SUM(f.snapshot_saturated_fat) as saturatedFat,
           SUM(f.snapshot_sodium) as sodium,
           SUM(f.snapshot_potassium) as potassium,
           SUM(f.snapshot_cholesterol) as cholesterol,
           SUM(f.snapshot_calcium) as calcium,
           SUM(f.snapshot_iron) as iron,
           SUM(f.snapshot_magnesium) as magnesium,
           SUM(f.snapshot_zinc) as zinc,
           SUM(f.snapshot_vitamin_a) as vitaminA,
           SUM(f.snapshot_vitamin_c) as vitaminC,
           SUM(f.snapshot_vitamin_d) as vitaminD,
           SUM(f.snapshot_vitamin_e) as vitaminE,
           SUM(f.snapshot_vitamin_b12) as vitaminB12
    FROM food_logs f
    JOIN food_items fi ON f.food_id = fi.id
    WHERE f.user_id = :userId
      AND f.log_date BETWEEN :start AND :end
    GROUP BY CAST(f.log_date AS DATE)
    ORDER BY CAST(f.log_date AS DATE)
    """, nativeQuery = true)
    List<Object[]> getDailyStatsByUserAndDateBetween(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query(value = """
SELECT
    COALESCE(SUM(COALESCE(f.snapshot_calories, COALESCE(fi.calories, 0) * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0)), 0),
    COALESCE(SUM(COALESCE(f.snapshot_protein, COALESCE(fi.protein, 0) * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0)), 0),
    COALESCE(SUM(COALESCE(f.snapshot_carbs, COALESCE(fi.carbs, 0) * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0)), 0),
    COALESCE(SUM(COALESCE(f.snapshot_fat, COALESCE(fi.fat, 0) * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0)), 0),
    SUM(f.snapshot_fiber),
    SUM(f.snapshot_sugar),
    SUM(f.snapshot_saturated_fat),
    SUM(f.snapshot_sodium),
    SUM(f.snapshot_potassium),
    SUM(f.snapshot_cholesterol),
    SUM(f.snapshot_calcium),
    SUM(f.snapshot_iron),
    SUM(f.snapshot_magnesium),
    SUM(f.snapshot_zinc),
    SUM(f.snapshot_vitamin_a),
    SUM(f.snapshot_vitamin_c),
    SUM(f.snapshot_vitamin_d),
    SUM(f.snapshot_vitamin_e),
    SUM(f.snapshot_vitamin_b12)
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

    @Query(value = """
            SELECT log_day
            FROM (
                SELECT CAST(f.log_date AS DATE) AS log_day
                FROM food_logs f
                WHERE f.user_id = :userId
                  AND f.log_date >= :start
                  AND f.log_date < :end
                UNION
                SELECT CAST(e.log_date AS DATE) AS log_day
                FROM exercise_logs e
                WHERE e.user_id = :userId
                  AND e.log_date >= :start
                  AND e.log_date < :end
            ) diary_days
            ORDER BY log_day DESC
            """, nativeQuery = true)
    List<Object> findDiaryEntryDates(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    long deleteByUser(UserEntity user);
}

