package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.SubscriptionProviderEventEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.PaymentProvider;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionProviderEventRepository extends JpaRepository<SubscriptionProviderEventEntity, Long>, JpaSpecificationExecutor<SubscriptionProviderEventEntity> {
    Optional<SubscriptionProviderEventEntity> findByProviderAndProviderEventId(PaymentProvider provider, String providerEventId);
    boolean existsByProviderAndProviderEventId(PaymentProvider provider, String providerEventId);
    long countByStatus(SubscriptionProviderEventStatus status);
    long countByReceivedAtAfter(LocalDateTime receivedAt);
    List<SubscriptionProviderEventEntity> findByUserOrderByReceivedAtDesc(UserEntity user);

    @Modifying
    @Query("""
            update SubscriptionProviderEventEntity event
            set event.user = null,
                event.providerAppUserId = :anonymizedAppUserId,
                event.rawPayload = :scrubbedPayload,
                event.processingError = null
            where event.user = :user
            """)
    int anonymizeUserReferences(@Param("user") UserEntity user,
                                @Param("anonymizedAppUserId") String anonymizedAppUserId,
                                @Param("scrubbedPayload") String scrubbedPayload);
}
