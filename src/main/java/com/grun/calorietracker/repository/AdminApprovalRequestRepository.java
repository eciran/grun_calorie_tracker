package com.grun.calorietracker.repository;
import com.grun.calorietracker.entity.AdminApprovalRequestEntity;
import com.grun.calorietracker.enums.AdminApprovalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.time.Instant;
public interface AdminApprovalRequestRepository extends JpaRepository<AdminApprovalRequestEntity,Long> {
 @Modifying
 @Query("update AdminApprovalRequestEntity a set a.status = com.grun.calorietracker.enums.AdminApprovalStatus.EXPIRED where a.status = com.grun.calorietracker.enums.AdminApprovalStatus.PENDING and a.expiresAt <= :now")
 int expirePending(@Param("now") Instant now);
 Page<AdminApprovalRequestEntity> findByStatus(AdminApprovalStatus status, Pageable pageable);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from AdminApprovalRequestEntity a where a.id=:id") Optional<AdminApprovalRequestEntity> findByIdForUpdate(@Param("id") Long id);
 long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);
 long countByStatus(AdminApprovalStatus status);
 long countByStatusAndDecidedAtGreaterThanEqualAndDecidedAtLessThan(AdminApprovalStatus status, Instant from, Instant to);
}
