package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AiDraftRejectReason;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AiRequestHistoryRepository extends JpaRepository<AiRequestHistoryEntity, Long> {
    List<AiRequestHistoryEntity> findByUserOrderByCreatedAtDesc(UserEntity user, Pageable pageable);
    List<AiRequestHistoryEntity> findByUserOrderByCreatedAtDesc(UserEntity user);
    List<AiRequestHistoryEntity> findByUserAndRequestTypeOrderByCreatedAtDesc(UserEntity user, AiRequestType requestType, Pageable pageable);
    List<AiRequestHistoryEntity> findByUserAndStatusOrderByCreatedAtDesc(UserEntity user, AiRequestStatus status, Pageable pageable);
    List<AiRequestHistoryEntity> findByUserAndRequestTypeAndStatusOrderByCreatedAtDesc(UserEntity user, AiRequestType requestType, AiRequestStatus status, Pageable pageable);
    Optional<AiRequestHistoryEntity> findByIdAndUser(Long id, UserEntity user);
    Optional<AiRequestHistoryEntity> findByUserAndRequestTypeAndIdempotencyKey(
            UserEntity user, AiRequestType requestType, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select history from AiRequestHistoryEntity history where history.id = :id")
    Optional<AiRequestHistoryEntity> findByIdForQuotaRefund(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select history from AiRequestHistoryEntity history
            where history.status in :statuses
              and history.completionNotifiedAt is null
            order by history.createdAt asc
            """)
    List<AiRequestHistoryEntity> findPendingCompletionNotifications(
            @Param("statuses") List<AiRequestStatus> statuses,
            Pageable pageable);
    Page<AiRequestHistoryEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<AiRequestHistoryEntity> findByStatusOrderByCreatedAtDesc(AiRequestStatus status, Pageable pageable);
    Page<AiRequestHistoryEntity> findByRequestTypeOrderByCreatedAtDesc(AiRequestType requestType, Pageable pageable);
    Page<AiRequestHistoryEntity> findByRequestTypeAndStatusOrderByCreatedAtDesc(AiRequestType requestType, AiRequestStatus status, Pageable pageable);
    @Query("""
            select history from AiRequestHistoryEntity history
            where history.status = com.grun.calorietracker.enums.AiRequestStatus.REJECTED
              and history.quotaConsumed = true
              and coalesce(history.quotaConsumedAmount, 0) > coalesce(history.quotaRefundedAmount, 0)
              and (history.quotaRefundDecision is null or history.quotaRefundDecision <> com.grun.calorietracker.enums.AiQuotaRefundDecision.REJECTED)
            order by history.createdAt desc
            """)
    Page<AiRequestHistoryEntity> findRefundableRejectedDrafts(Pageable pageable);
    @Query("""
            select count(history)
            from AiRequestHistoryEntity history
            where history.status = com.grun.calorietracker.enums.AiRequestStatus.REJECTED
              and history.quotaConsumed = true
              and coalesce(history.quotaConsumedAmount, 0) > coalesce(history.quotaRefundedAmount, 0)
              and (history.quotaRefundDecision is null or history.quotaRefundDecision <> com.grun.calorietracker.enums.AiQuotaRefundDecision.REJECTED)
            """)
    long countRefundableRejectedDrafts();
    long countByUser(UserEntity user);
    long countByCreatedAtAfter(LocalDateTime createdAt);
    long countByStatusAndCreatedAtAfter(AiRequestStatus status, LocalDateTime createdAt);
    long countByRejectionReasonAndRejectedAtAfter(AiDraftRejectReason rejectionReason, LocalDateTime rejectedAt);
    @Query("""
            select history.rejectionReason, count(history)
            from AiRequestHistoryEntity history
            where history.status = com.grun.calorietracker.enums.AiRequestStatus.REJECTED
              and history.rejectedAt >= :rejectedAfter
              and history.rejectionReason is not null
            group by history.rejectionReason
            """)
    List<Object[]> countRejectedDraftsByReasonAfter(@Param("rejectedAfter") LocalDateTime rejectedAfter);
    @Query("""
            select history.provider,
                   history.model,
                   history.promptVersion,
                   history.costCurrency,
                   count(history),
                   coalesce(sum(history.promptTokens), 0),
                   coalesce(sum(history.completionTokens), 0),
                   coalesce(sum(history.totalTokens), 0),
                   coalesce(sum(history.estimatedCost), 0),
                   coalesce(sum(history.quotaConsumedAmount), 0),
                   coalesce(sum(history.quotaRefundedAmount), 0)
            from AiRequestHistoryEntity history
            where history.createdAt >= :createdAfter
            group by history.provider, history.model, history.promptVersion, history.costCurrency
            order by count(history) desc
            """)
    List<Object[]> summarizeByProviderModelAfter(@Param("createdAfter") LocalDateTime createdAfter);

    @Query("""
            select history.requestType,
                   history.status,
                   count(history),
                   coalesce(sum(history.totalTokens), 0),
                   coalesce(sum(history.quotaConsumedAmount), 0),
                   coalesce(sum(history.quotaRefundedAmount), 0)
            from AiRequestHistoryEntity history
            where history.createdAt >= :createdAfter
            group by history.requestType, history.status
            order by history.requestType, history.status
            """)
    List<Object[]> summarizeByRequestTypeStatusAfter(@Param("createdAfter") LocalDateTime createdAfter);
    void deleteByUser(UserEntity user);
}


