package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AiDraftRejectReason;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
    Page<AiRequestHistoryEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<AiRequestHistoryEntity> findByStatusOrderByCreatedAtDesc(AiRequestStatus status, Pageable pageable);
    Page<AiRequestHistoryEntity> findByRequestTypeOrderByCreatedAtDesc(AiRequestType requestType, Pageable pageable);
    Page<AiRequestHistoryEntity> findByRequestTypeAndStatusOrderByCreatedAtDesc(AiRequestType requestType, AiRequestStatus status, Pageable pageable);
    @Query("""
            select history from AiRequestHistoryEntity history
            where history.status = com.grun.calorietracker.enums.AiRequestStatus.REJECTED
              and history.quotaConsumed = true
              and coalesce(history.quotaConsumedAmount, 0) > coalesce(history.quotaRefundedAmount, 0)
            order by history.createdAt desc
            """)
    Page<AiRequestHistoryEntity> findRefundableRejectedDrafts(Pageable pageable);
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
    void deleteByUser(UserEntity user);
}


