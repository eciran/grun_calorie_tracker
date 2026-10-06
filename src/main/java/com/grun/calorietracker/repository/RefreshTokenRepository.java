package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.RefreshTokenEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update RefreshTokenEntity t set t.usedAt = :now where t.tokenHash = :hash and t.usedAt is null and t.revokedAt is null and t.expiresAt > :now and t.authTokenVersion = :version")
    int consumeIfAvailable(@org.springframework.data.repository.query.Param("hash") String hash,
                           @org.springframework.data.repository.query.Param("now") LocalDateTime now,
                           @org.springframework.data.repository.query.Param("version") long version);

    Optional<RefreshTokenEntity> findByTokenHashAndRevokedAtIsNullAndUsedAtIsNull(String tokenHash);

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update RefreshTokenEntity t set t.revokedAt = :now where t.tokenHash = :hash and t.usedAt is null and t.revokedAt is null")
    int revokeIfAvailable(@org.springframework.data.repository.query.Param("hash") String hash,
                          @org.springframework.data.repository.query.Param("now") LocalDateTime now);

    List<RefreshTokenEntity> findByUserAndRevokedAtIsNullAndUsedAtIsNull(UserEntity user);

    long deleteByExpiresAtBefore(LocalDateTime cutoff);

    long deleteByUser(UserEntity user);
}
