package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.MealReminderDeliveryAttemptEntity;
import com.grun.calorietracker.enums.MealReminderAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MealReminderDeliveryAttemptRepository extends JpaRepository<MealReminderDeliveryAttemptEntity, Long> {
    long countByStatus(MealReminderAttemptStatus status);
    Optional<MealReminderDeliveryAttemptEntity> findByOutboxIdAndPushTokenId(Long outboxId, Long pushTokenId);
    List<MealReminderDeliveryAttemptEntity> findByOutboxIdOrderById(Long outboxId);
    List<MealReminderDeliveryAttemptEntity> findByStatusAndNextAttemptAtLessThanEqual(
            MealReminderAttemptStatus status, Instant now);
    Optional<MealReminderDeliveryAttemptEntity> findByProviderMessageId(String providerMessageId);

    @Query("""
            select attempt from MealReminderDeliveryAttemptEntity attempt
            join fetch attempt.pushToken token
            join fetch token.user
            join fetch attempt.outbox outbox
            join fetch outbox.notification
            where attempt.id = :id
            """)
    Optional<MealReminderDeliveryAttemptEntity> findByIdWithPayload(@Param("id") Long id);
}
