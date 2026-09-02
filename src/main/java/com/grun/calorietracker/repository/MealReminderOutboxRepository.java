package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.MealReminderOutboxEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MealReminderOutboxRepository extends JpaRepository<MealReminderOutboxEntity, Long> {
    @Query(value = """
            SELECT * FROM meal_reminder_outbox
            WHERE status IN ('PENDING', 'RETRY', 'PROCESSING')
              AND available_at <= :now
              AND expires_at > :now
              AND (lease_until IS NULL OR lease_until < :now)
            ORDER BY available_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<MealReminderOutboxEntity> lockDue(
            @Param("now") Instant now,
            @Param("limit") int limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select outbox from MealReminderOutboxEntity outbox join fetch outbox.occurrence occurrence join fetch occurrence.user where outbox.id = :id")
    Optional<MealReminderOutboxEntity> findByIdForUpdate(@Param("id") Long id);
}
