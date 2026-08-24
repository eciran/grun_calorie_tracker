package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AdminInvitationEntity;
import com.grun.calorietracker.enums.AdminInvitationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AdminInvitationRepository extends JpaRepository<AdminInvitationEntity, Long> {
    Page<AdminInvitationEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<AdminInvitationEntity> findByEmailIgnoreCaseAndStatus(String email, AdminInvitationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from AdminInvitationEntity invitation where invitation.tokenHash = :tokenHash")
    Optional<AdminInvitationEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
