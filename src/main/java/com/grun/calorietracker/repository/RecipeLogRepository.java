package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.RecipeLogEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RecipeLogRepository extends JpaRepository<RecipeLogEntity, Long> {
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

    @Query(value = """
            SELECT DATE(r.log_date) as logDate,
                   SUM(COALESCE(r.snapshot_calories, 0)) as calories,
                   SUM(COALESCE(r.snapshot_protein, 0)) as protein,
                   SUM(COALESCE(r.snapshot_carbs, 0)) as carbs,
                   SUM(COALESCE(r.snapshot_fat, 0)) as fat
            FROM recipe_logs r
            WHERE r.user_id = :userId
              AND r.log_date BETWEEN :start AND :end
            GROUP BY DATE(r.log_date)
            ORDER BY DATE(r.log_date)
            """, nativeQuery = true)
    List<Object[]> getDailyStatsByUserAndDateBetween(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
