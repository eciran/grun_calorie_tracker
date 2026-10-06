package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AdminMfaRecoveryCodeEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminMfaRecoveryCodeRepository extends JpaRepository<AdminMfaRecoveryCodeEntity, Long> {
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update AdminMfaRecoveryCodeEntity c set c.usedAt = :now where c.id = :id and c.user.id = :userId and c.usedAt is null")
    int consumeIfAvailable(@org.springframework.data.repository.query.Param("id") Long id,
                           @org.springframework.data.repository.query.Param("userId") Long userId,
                           @org.springframework.data.repository.query.Param("now") java.time.Instant now);
    List<AdminMfaRecoveryCodeEntity> findByUserAndUsedAtIsNull(UserEntity user);
    long countByUserAndUsedAtIsNull(UserEntity user);
    void deleteByUser(UserEntity user);
}
