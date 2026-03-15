package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ExerciseLogsEntity;
import com.grun.calorietracker.entity.UserEntity;
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
  //  List<ExerciseLogsEntity> findByUserAndLogDateBetween(UserEntity user, LocalDateTime start, LocalDateTime end);
    Optional<ExerciseLogsEntity> findByIdAndUser(Long id, UserEntity user);
    List<ExerciseLogsEntity> findByUserAndSource(UserEntity user, String source);
    Optional<ExerciseLogsEntity> findByExternalIdAndUser(String externalId, UserEntity user);

    @Query(value = """
    SELECT date_trunc(:range, e.log_date) as bucket,
           SUM(e.duration_minutes) as total_duration,
           SUM(e.calories_burned) as total_calories
    FROM exercise_logs e
    WHERE e.user_id = :userId
      AND e.log_date BETWEEN :startDate AND :endDate
    GROUP BY date_trunc(:range, e.log_date)
    ORDER BY bucket ASC
    """, nativeQuery = true)
    List<Object[]> findByUserAndLogDateBetween(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("range") String range
    );
}
