package com.grun.calorietracker.repository;
import com.grun.calorietracker.entity.AdminApprovalRequestEntity;
import com.grun.calorietracker.enums.AdminApprovalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface AdminApprovalRequestRepository extends JpaRepository<AdminApprovalRequestEntity,Long> {
 Page<AdminApprovalRequestEntity> findByStatus(AdminApprovalStatus status, Pageable pageable);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from AdminApprovalRequestEntity a where a.id=:id") Optional<AdminApprovalRequestEntity> findByIdForUpdate(@Param("id") Long id);
}