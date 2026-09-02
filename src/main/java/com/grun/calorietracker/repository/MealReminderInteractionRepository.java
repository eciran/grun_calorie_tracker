package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.MealReminderInteractionEntity;
import com.grun.calorietracker.enums.MealReminderInteractionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public interface MealReminderInteractionRepository extends JpaRepository<MealReminderInteractionEntity, Long> {
    @Modifying
    @Query(value = """
            INSERT INTO meal_reminder_interactions
              (user_id, occurrence_id, notification_id, event_type, event_key, client_event_id, source, related_date, recorded_at)
            VALUES (:userId, :occurrenceId, :notificationId, :eventType, :eventKey, :clientEventId, :source, :relatedDate, :recordedAt)
            ON CONFLICT (event_key) DO NOTHING
            """, nativeQuery = true)
    int insertIdempotent(@Param("userId") Long userId,
                         @Param("occurrenceId") Long occurrenceId,
                         @Param("notificationId") Long notificationId,
                         @Param("eventType") String eventType,
                         @Param("eventKey") String eventKey,
                         @Param("clientEventId") String clientEventId,
                         @Param("source") String source,
                         @Param("relatedDate") LocalDate relatedDate,
                         @Param("recordedAt") Instant recordedAt);

    Optional<MealReminderInteractionEntity> findFirstByUserIdAndRelatedDateAndEventTypeAndRecordedAtBetweenOrderByRecordedAtDesc(
            Long userId, LocalDate relatedDate, MealReminderInteractionType eventType,
            Instant fromInclusive, Instant toInclusive);

    long countByEventType(MealReminderInteractionType eventType);

    @Query("select count(distinct interaction.occurrence.id) from MealReminderInteractionEntity interaction "
            + "where interaction.eventType = :eventType and interaction.occurrence is not null")
    long countDistinctOccurrencesByEventType(@Param("eventType") MealReminderInteractionType eventType);
}
