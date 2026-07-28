package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FastingReminderDeliveryEntity;
import com.grun.calorietracker.enums.FastingReminderDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FastingReminderDeliveryRepository extends JpaRepository<FastingReminderDeliveryEntity, Long> {
    Optional<FastingReminderDeliveryEntity> findByOccurrenceKey(String occurrenceKey);
    long countByStatus(FastingReminderDeliveryStatus status);

    @Modifying
    @Query("""
            update FastingReminderDeliveryEntity delivery
            set delivery.status = com.grun.calorietracker.enums.FastingReminderDeliveryStatus.SUPPRESSED,
                delivery.nextAttemptAt = null,
                delivery.lastError = :reason
            where delivery.occurrence.user.id = :userId
              and delivery.status in (
                com.grun.calorietracker.enums.FastingReminderDeliveryStatus.PENDING,
                com.grun.calorietracker.enums.FastingReminderDeliveryStatus.DEFERRED,
                com.grun.calorietracker.enums.FastingReminderDeliveryStatus.FAILED
              )
            """)
    int suppressUndeliveredForUser(@Param("userId") Long userId, @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from FastingReminderDeliveryEntity delivery where delivery.occurrence.id = :occurrenceId and delivery.status in (com.grun.calorietracker.enums.FastingReminderDeliveryStatus.PENDING, com.grun.calorietracker.enums.FastingReminderDeliveryStatus.DEFERRED, com.grun.calorietracker.enums.FastingReminderDeliveryStatus.FAILED)")
    int deleteUndeliveredForOccurrence(@Param("occurrenceId") Long occurrenceId);
}