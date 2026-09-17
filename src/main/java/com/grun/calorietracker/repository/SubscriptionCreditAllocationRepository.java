package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.SubscriptionCreditAllocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface SubscriptionCreditAllocationRepository extends JpaRepository<SubscriptionCreditAllocationEntity, Long> {
    @Modifying
    @Query(value = """
            UPDATE subscription_credit_allocations SET upgrade_bonus = :bonus
             WHERE user_id = :userId AND provider = :provider AND allocation_key = :allocationKey
            """, nativeQuery = true)
    int recordUpgradeBonus(@Param("userId") Long userId, @Param("provider") String provider,
                           @Param("allocationKey") String allocationKey, @Param("bonus") int bonus);

    @Modifying
    @Query(value = """
            INSERT INTO subscription_credit_allocations
              (user_id, provider, allocation_key, plan_type, quota_amount, purchased_at, expires_at,
               transaction_id, original_transaction_id, first_provider_event_id, created_at)
            VALUES (:userId, :provider, :allocationKey, :planType, :quotaAmount, :purchasedAt, :expiresAt,
                    :transactionId, :originalTransactionId, :providerEventId, :createdAt)
            ON CONFLICT (provider, user_id, allocation_key) DO NOTHING
            """, nativeQuery = true)
    int reserve(@Param("userId") Long userId,
                @Param("provider") String provider,
                @Param("allocationKey") String allocationKey,
                @Param("planType") String planType,
                @Param("quotaAmount") int quotaAmount,
                @Param("purchasedAt") Instant purchasedAt,
                @Param("expiresAt") Instant expiresAt,
                @Param("transactionId") String transactionId,
                @Param("originalTransactionId") String originalTransactionId,
                @Param("providerEventId") String providerEventId,
                @Param("createdAt") Instant createdAt);

    @Modifying
    @Query(value = """
            UPDATE subscription_credit_allocations
               SET revoked_at = :revokedAt,
                   revoked_by_event_id = :providerEventId,
                   revocation_reason = :reason
             WHERE user_id = :userId
               AND provider = :provider
               AND revoked_at IS NULL
               AND (transaction_id = :transactionId
                    OR (:transactionId IS NULL AND original_transaction_id = :originalTransactionId))
            """, nativeQuery = true)
    int revokeForRefund(@Param("userId") Long userId,
                        @Param("provider") String provider,
                        @Param("transactionId") String transactionId,
                        @Param("originalTransactionId") String originalTransactionId,
                        @Param("providerEventId") String providerEventId,
                        @Param("reason") String reason,
                        @Param("revokedAt") Instant revokedAt);

    @Modifying
    @Query(value = """
            UPDATE subscription_credit_allocations
               SET revoked_at = NULL,
                   revoked_by_event_id = NULL,
                   revocation_reason = NULL
             WHERE user_id = :userId
               AND provider = :provider
               AND revoked_at IS NOT NULL
               AND (transaction_id = :transactionId
                    OR (:transactionId IS NULL AND original_transaction_id = :originalTransactionId))
            """, nativeQuery = true)
    int restoreAfterRefundReversal(@Param("userId") Long userId,
                                   @Param("provider") String provider,
                                   @Param("transactionId") String transactionId,
                                   @Param("originalTransactionId") String originalTransactionId);
}
