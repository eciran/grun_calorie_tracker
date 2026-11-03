package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AiQuotaPromotionReservationEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface AiQuotaPromotionReservationRepository extends JpaRepository<AiQuotaPromotionReservationEntity, Long> {
    long countByUserAndImpressionAtAfter(UserEntity user, Instant after);
    boolean existsByUserAndSessionIdAndImpressionAtIsNotNull(UserEntity user, String sessionId);
    boolean existsByUserAndImpressionAtAfter(UserEntity user, Instant after);
    boolean existsByUserAndDismissedAtAfter(UserEntity user, Instant after);
    boolean existsByUserAndSessionIdAndImpressionAtIsNullAndExpiresAtAfter(UserEntity user, String sessionId, Instant now);
    Optional<AiQuotaPromotionReservationEntity> findByReservationTokenAndUser(String reservationToken, UserEntity user);
}
