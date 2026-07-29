package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AdminSessionEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AdminSessionRepository extends JpaRepository<AdminSessionEntity, String> {
    Optional<AdminSessionEntity> findBySessionTokenHashAndRevokedAtIsNull(String sessionTokenHash);
    List<AdminSessionEntity> findByUserAndRevokedAtIsNull(UserEntity user);
    Page<AdminSessionEntity> findByUserAndRevokedAtIsNullOrderByCreatedAtDesc(UserEntity user, Pageable pageable);
    @Query("select session from AdminSessionEntity session where session.revokedAt is null and session.absoluteExpiresAt > :now and session.lastActivityAt > :idleCutoff order by session.lastActivityAt desc")
    Page<AdminSessionEntity> findActiveSessions(@Param("now") Instant now, @Param("idleCutoff") Instant idleCutoff, Pageable pageable);
}
