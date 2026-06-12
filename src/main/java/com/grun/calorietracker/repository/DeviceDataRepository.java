package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.DeviceDataEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.HealthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DeviceDataRepository extends JpaRepository<DeviceDataEntity, Long> {
    long countByUser(UserEntity user);
    List<DeviceDataEntity> findByUserOrderByRecordedAtAsc(UserEntity user);
    Optional<DeviceDataEntity> findByUserAndProviderAndExternalId(UserEntity user, HealthProvider provider, String externalId);

    Optional<DeviceDataEntity> findByUserAndProviderAndExternalIdIsNullAndRecordedAt(
            UserEntity user,
            HealthProvider provider,
            LocalDateTime recordedAt
    );

    List<DeviceDataEntity> findByUserAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
            UserEntity user,
            LocalDateTime start,
            LocalDateTime end
    );

    long deleteByUserAndProvider(UserEntity user, HealthProvider provider);

    long deleteByUser(UserEntity user);

    List<DeviceDataEntity> findByUserAndProviderAndRecordedAtBetweenOrderByRecordedAtAsc(
            UserEntity user,
            HealthProvider provider,
            LocalDateTime start,
            LocalDateTime end
    );

    @Query("""
            SELECT COUNT(d)
            FROM DeviceDataEntity d
            WHERE d.recordedAt >= :recordedAt
              AND d.steps IS NOT NULL
            """)
    long countStepRecordsAfter(@Param("recordedAt") LocalDateTime recordedAt);

    @Query("""
            SELECT COUNT(DISTINCT d.user.id)
            FROM DeviceDataEntity d
            WHERE d.recordedAt >= :recordedAt
              AND d.steps IS NOT NULL
            """)
    long countDistinctStepUsersAfter(@Param("recordedAt") LocalDateTime recordedAt);

    @Query("""
            SELECT COALESCE(SUM(d.steps), 0)
            FROM DeviceDataEntity d
            WHERE d.recordedAt >= :recordedAt
              AND d.steps IS NOT NULL
            """)
    long sumStepsAfter(@Param("recordedAt") LocalDateTime recordedAt);

    @Query(value = """
            SELECT CAST(recorded_at AS DATE) AS metric_date,
                   COUNT(*) AS record_count,
                   COUNT(DISTINCT user_id) AS user_count,
                   COALESCE(SUM(steps), 0) AS total_steps
            FROM device_data
            WHERE CAST(recorded_at AS DATE) BETWEEN :startDate AND :endDate
              AND steps IS NOT NULL
            GROUP BY CAST(recorded_at AS DATE)
            ORDER BY CAST(recorded_at AS DATE)
            """, nativeQuery = true)
    List<Object[]> aggregateDailySteps(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
