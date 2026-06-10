package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.PasswordResetTokenEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Long> {

    Optional<PasswordResetTokenEntity> findByTokenHashAndUsedAtIsNull(String tokenHash);

    List<PasswordResetTokenEntity> findByUserAndUsedAtIsNull(UserEntity user);

    Optional<PasswordResetTokenEntity> findTopByUserOrderByCreatedAtDesc(UserEntity user);

    long deleteByExpiresAtBefore(LocalDateTime cutoff);

    long deleteByUser(UserEntity user);
}
