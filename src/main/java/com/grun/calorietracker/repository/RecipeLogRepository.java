package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.RecipeLogEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.projection.MealReminderMealAggregateProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RecipeLogRepository extends JpaRepository<RecipeLogEntity, Long> {
    @Query(value = """
            SELECT r.user_id AS userId,
                   UPPER(r.meal_type) AS mealType,
                   COUNT(*) AS recordCount,
                   COALESCE(SUM(COALESCE(r.snapshot_calories, 0)), 0) AS calories
            FROM recipe_logs r
            WHERE r.user_id IN (:userIds)
              AND r.log_date >= :start
              AND r.log_date < :end
            GROUP BY r.user_id, UPPER(r.meal_type)
            """, nativeQuery = true)
    List<MealReminderMealAggregateProjection> aggregateMealReminderByUsersAndDate(
            @Param("userIds") List<Long> userIds,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    long countByUser(UserEntity user);

    List<RecipeLogEntity> findByUserOrderByLogDateAsc(UserEntity user);

    List<RecipeLogEntity> findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
            UserEntity user,
            LocalDateTime start,
            LocalDateTime end
    );

    List<RecipeLogEntity> findByUserAndMealTypeAndLogDateBetween(
            UserEntity user,
            String mealType,
            LocalDateTime start,
            LocalDateTime end
    );

    Optional<RecipeLogEntity> findByIdAndUser(Long id, UserEntity user);

    long deleteByUser(UserEntity user);

    @Query(value = """
            SELECT CAST(r.log_date AS DATE) as logDate,
                   SUM(COALESCE(r.snapshot_calories, 0)) as calories,
                   SUM(COALESCE(r.snapshot_protein, 0)) as protein,
                   SUM(COALESCE(r.snapshot_carbs, 0)) as carbs,
                   SUM(COALESCE(r.snapshot_fat, 0)) as fat,
                   SUM(r.snapshot_fiber) as fiber,
                   SUM(r.snapshot_sugar) as sugar,
                   SUM(r.snapshot_saturated_fat) as saturatedFat,
                   SUM(r.snapshot_sodium) as sodium,
                   SUM(r.snapshot_potassium) as potassium,
                   SUM(r.snapshot_cholesterol) as cholesterol,
                   SUM(r.snapshot_calcium) as calcium,
                   SUM(r.snapshot_iron) as iron,
                   SUM(r.snapshot_magnesium) as magnesium,
                   SUM(r.snapshot_zinc) as zinc,
                   SUM(r.snapshot_vitamin_a) as vitaminA,
                   SUM(r.snapshot_vitamin_c) as vitaminC,
                   SUM(r.snapshot_vitamin_d) as vitaminD,
                   SUM(r.snapshot_vitamin_e) as vitaminE,
                   SUM(r.snapshot_vitamin_b12) as vitaminB12
            FROM recipe_logs r
            WHERE r.user_id = :userId
              AND r.log_date >= :start
              AND r.log_date < :end
            GROUP BY CAST(r.log_date AS DATE)
            ORDER BY CAST(r.log_date AS DATE)
            """, nativeQuery = true)
    List<Object[]> getDailyStatsByUserAndDateBetween(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = """
            SELECT COALESCE(SUM(r.snapshot_calories), 0),
                   COALESCE(SUM(r.snapshot_protein), 0),
                   COALESCE(SUM(r.snapshot_carbs), 0),
                   COALESCE(SUM(r.snapshot_fat), 0),
                   SUM(r.snapshot_fiber),
                   SUM(r.snapshot_sugar),
                   SUM(r.snapshot_saturated_fat),
                   SUM(r.snapshot_sodium),
                   SUM(r.snapshot_potassium),
                   SUM(r.snapshot_cholesterol),
                   SUM(r.snapshot_calcium),
                   SUM(r.snapshot_iron),
                   SUM(r.snapshot_magnesium),
                   SUM(r.snapshot_zinc),
                   SUM(r.snapshot_vitamin_a),
                   SUM(r.snapshot_vitamin_c),
                   SUM(r.snapshot_vitamin_d),
                   SUM(r.snapshot_vitamin_e),
                   SUM(r.snapshot_vitamin_b12)
            FROM recipe_logs r
            WHERE r.user_id = :userId
              AND r.log_date >= :start
              AND r.log_date < :end
            """, nativeQuery = true)
    List<Object[]> getSummaryTotalsByUserAndDateBetween(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
