package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.MealReminderScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MealReminderScheduleRepository extends JpaRepository<MealReminderScheduleEntity, Long> {
    @Modifying
    @Query(value = """
            INSERT INTO meal_reminder_schedules(user_id, next_evaluation_at, updated_at)
            SELECT u.id, :now, :now
            FROM users u
            WHERE u.account_enabled = TRUE
              AND u.account_locked = FALSE
              AND NOT EXISTS (SELECT 1 FROM meal_reminder_schedules schedule WHERE schedule.user_id = u.id)
            ORDER BY u.id
            LIMIT :limit
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    int bootstrapMissing(@Param("now") Instant now, @Param("limit") int limit);

    @Query(value = """
            SELECT * FROM meal_reminder_schedules
            WHERE next_evaluation_at <= :now
              AND (lease_until IS NULL OR lease_until < :now)
            ORDER BY next_evaluation_at, user_id
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<MealReminderScheduleEntity> lockDue(
            @Param("now") Instant now,
            @Param("limit") int limit);
}
