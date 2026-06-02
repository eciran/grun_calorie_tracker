package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AiDraftRejectReason;
import com.grun.calorietracker.enums.AiRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AiRequestHistoryRepository extends JpaRepository<AiRequestHistoryEntity, Long> {
    List<AiRequestHistoryEntity> findByUserOrderByCreatedAtDesc(UserEntity user, Pageable pageable);
    List<AiRequestHistoryEntity> findByUserOrderByCreatedAtDesc(UserEntity user);
    Optional<AiRequestHistoryEntity> findByIdAndUser(Long id, UserEntity user);
    Page<AiRequestHistoryEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<AiRequestHistoryEntity> findByStatusOrderByCreatedAtDesc(AiRequestStatus status, Pageable pageable);
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
    void deleteByUser(UserEntity user);
}
