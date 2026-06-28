package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.WaterLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WaterLogRepository extends JpaRepository<WaterLogEntity, Long> {
    List<WaterLogEntity> findByUserAndLogDateOrderByLoggedAtAsc(UserEntity user, LocalDate logDate);
    List<WaterLogEntity> findByUserOrderByLoggedAtAsc(UserEntity user);
    Optional<WaterLogEntity> findByIdAndUser(Long id, UserEntity user);
    Optional<WaterLogEntity> findTopByUserOrderByLoggedAtDesc(UserEntity user);
    long countByUser(UserEntity user);
    long countByLoggedAtAfter(LocalDateTime loggedAt);
    long deleteByUser(UserEntity user);

    @Query("""
            SELECT COUNT(DISTINCT w.user.id)
            FROM WaterLogEntity w
            WHERE w.loggedAt >= :loggedAt
            """)
    long countDistinctUsersByLoggedAtAfter(@Param("loggedAt") LocalDateTime loggedAt);

    @Query("""
            SELECT COALESCE(SUM(w.amountMl), 0)
            FROM WaterLogEntity w
            WHERE w.loggedAt >= :loggedAt
            """)
    long sumAmountMlByLoggedAtAfter(@Param("loggedAt") LocalDateTime loggedAt);

    @Query("""
            SELECT w.logDate, COUNT(w), COUNT(DISTINCT w.user.id), COALESCE(SUM(w.amountMl), 0)
            FROM WaterLogEntity w
            WHERE w.logDate BETWEEN :startDate AND :endDate
            GROUP BY w.logDate
            ORDER BY w.logDate
            """)
    List<Object[]> aggregateDailyWater(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            SELECT COALESCE(SUM(w.amountMl), 0)
            FROM WaterLogEntity w
            WHERE w.user = :user
              AND w.logDate = :logDate
            """)
    Long sumAmountMlByUserAndLogDate(@Param("user") UserEntity user, @Param("logDate") LocalDate logDate);


    @Query("""
            SELECT w.logDate, COUNT(w), COALESCE(SUM(w.amountMl), 0)
            FROM WaterLogEntity w
            WHERE w.user = :user
              AND w.logDate BETWEEN :startDate AND :endDate
            GROUP BY w.logDate
            ORDER BY w.logDate
            """)
    List<Object[]> aggregateDailyWaterByUser(
            @Param("user") UserEntity user,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
    @Query(value = """
            SELECT COALESCE(MAX(daily_total), 0)
            FROM (
                SELECT log_date, SUM(amount_ml) AS daily_total
                FROM water_logs
                WHERE user_id = :userId
                GROUP BY log_date
            ) daily_water
            """, nativeQuery = true)
    int maxDailyAmountMlByUserId(@Param("userId") Long userId);

    List<WaterLogEntity> findByUserAndLoggedAtBetweenOrderByLoggedAtAsc(
            UserEntity user,
            LocalDateTime start,
            LocalDateTime end
    );
}
