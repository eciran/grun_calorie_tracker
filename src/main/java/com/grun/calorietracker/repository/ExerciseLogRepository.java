package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ExerciseLogsEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExerciseLogRepository extends JpaRepository<ExerciseLogsEntity, Long> {
    List<ExerciseLogsEntity> findByUser(UserEntity user);
    long countByUser(UserEntity user);
    Optional<ExerciseLogsEntity> findTopByUserOrderByLogDateDesc(UserEntity user);
    List<ExerciseLogsEntity> findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
            UserEntity user,
            LocalDateTime start,
            LocalDateTime end
    );
  //  List<ExerciseLogsEntity> findByUserAndLogDateBetween(UserEntity user, LocalDateTime start, LocalDateTime end);
    Optional<ExerciseLogsEntity> findByIdAndUser(Long id, UserEntity user);
    List<ExerciseLogsEntity> findByUserAndSource(UserEntity user, String source);
    Page<ExerciseLogsEntity> findByUserAndSource(UserEntity user, String source, Pageable pageable);
    Optional<ExerciseLogsEntity> findByUserAndSourceAndExternalId(UserEntity user, String source, String externalId);

    @Query(value = """
            SELECT COALESCE(SUM(e.calories_burned), 0)
            FROM exercise_logs e
            WHERE e.user_id = :userId
              AND e.log_date >= :start
              AND e.log_date < :end
            """, nativeQuery = true)
    double sumCaloriesBurnedByUserIdAndLogDateBetween(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = """
    SELECT bucket,
           COALESCE(SUM(duration_minutes), 0) as total_duration,
           COALESCE(SUM(calories_burned), 0) as total_calories
    FROM (
        SELECT CASE
                   WHEN :range = 'week' THEN date_trunc('week', e.log_date)
                   WHEN :range = 'month' THEN date_trunc('month', e.log_date)
                   ELSE date_trunc('day', e.log_date)
               END AS bucket,
               e.duration_minutes,
               e.calories_burned
        FROM exercise_logs e
        WHERE e.user_id = :userId
          AND e.log_date BETWEEN :startDate AND :endDate
    ) grouped_exercise_logs
    GROUP BY bucket
    ORDER BY bucket ASC
    """, nativeQuery = true)
    List<Object[]> findByUserAndLogDateBetween(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("range") String range
    );

    @Query(value = """
    SELECT
        COALESCE(SUM(e.calories_burned), 0),
        COALESCE(SUM(e.duration_minutes), 0)
    FROM exercise_logs e
    WHERE e.user_id = :userId
      AND e.log_date >= :start
      AND e.log_date < :end
    """, nativeQuery = true)
    List<Object[]> getSummaryTotalsByUserAndDateBetween(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    long deleteByUser(UserEntity user);
}

