package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.NotificationDeliveryAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationDeliveryAttemptRepository extends JpaRepository<NotificationDeliveryAttemptEntity, Long> {
    Optional<NotificationDeliveryAttemptEntity> findByOutboxIdAndPushTokenId(Long outboxId, Long pushTokenId);

    List<NotificationDeliveryAttemptEntity> findByOutboxIdOrderById(Long outboxId);

    Optional<NotificationDeliveryAttemptEntity> findByProviderMessageId(String providerMessageId);

    @Query("select attempt.status, count(attempt) from NotificationDeliveryAttemptEntity attempt "
            + "where attempt.outbox.occurrence.classification = :classification group by attempt.status")
    List<Object[]> countStatuses(@Param("classification") com.grun.calorietracker.enums.NotificationClassification classification);

    @Query("select attempt from NotificationDeliveryAttemptEntity attempt "
            + "join fetch attempt.outbox outbox join fetch outbox.notification "
            + "join fetch attempt.pushToken where attempt.id = :id")
    Optional<NotificationDeliveryAttemptEntity> findByIdWithPayload(@Param("id") Long id);
}
