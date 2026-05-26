package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.SubscriptionProviderEventEntity;
import com.grun.calorietracker.enums.PaymentProvider;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SubscriptionProviderEventRepository extends JpaRepository<SubscriptionProviderEventEntity, Long>, JpaSpecificationExecutor<SubscriptionProviderEventEntity> {
    Optional<SubscriptionProviderEventEntity> findByProviderAndProviderEventId(PaymentProvider provider, String providerEventId);
    boolean existsByProviderAndProviderEventId(PaymentProvider provider, String providerEventId);
    long countByStatus(SubscriptionProviderEventStatus status);
    long countByReceivedAtAfter(LocalDateTime receivedAt);
}
