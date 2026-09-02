package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.Optional;

public interface FreePromotionReservationRepository extends JpaRepository<FreePromotionReservationEntity, Long> {
    long countByUserAndImpressionAtAfter(UserEntity user, Instant after);
    boolean existsByUserAndSessionIdAndImpressionAtIsNotNull(UserEntity user, String sessionId);
    boolean existsByUserAndImpressionAtAfter(UserEntity user, Instant after);
    boolean existsByUserAndDismissedAtAfter(UserEntity user, Instant after);
    boolean existsByUserAndSessionIdAndImpressionAtIsNullAndExpiresAtAfter(UserEntity user, String sessionId, Instant now);
    Optional<FreePromotionReservationEntity> findByReservationTokenAndUser(String reservationToken, UserEntity user);
}
