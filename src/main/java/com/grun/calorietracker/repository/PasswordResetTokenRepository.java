package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.PasswordResetTokenEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Long> {

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update PasswordResetTokenEntity t set t.usedAt = :now where t.tokenHash = :hash and t.usedAt is null and t.expiresAt > :now")
    int consumeIfAvailable(@org.springframework.data.repository.query.Param("hash") String hash,
                           @org.springframework.data.repository.query.Param("now") LocalDateTime now);

    Optional<PasswordResetTokenEntity> findByTokenHashAndUsedAtIsNull(String tokenHash);

    List<PasswordResetTokenEntity> findByUserAndUsedAtIsNull(UserEntity user);

    Optional<PasswordResetTokenEntity> findTopByUserOrderByCreatedAtDesc(UserEntity user);

    long deleteByExpiresAtBefore(LocalDateTime cutoff);

    long deleteByUser(UserEntity user);
}
