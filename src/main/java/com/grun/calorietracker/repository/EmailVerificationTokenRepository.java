package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.EmailVerificationTokenEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationTokenEntity, Long> {

    Optional<EmailVerificationTokenEntity> findByTokenHashAndUsedAtIsNull(String tokenHash);

    List<EmailVerificationTokenEntity> findByUserAndUsedAtIsNull(UserEntity user);

    Optional<EmailVerificationTokenEntity> findTopByUserOrderByCreatedAtDesc(UserEntity user);

    long countByUserAndCreatedAtAfter(UserEntity user, LocalDateTime createdAtAfter);

    long deleteByExpiresAtBefore(LocalDateTime cutoff);

    long deleteByUser(UserEntity user);
}
