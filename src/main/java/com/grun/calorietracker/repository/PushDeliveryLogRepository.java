package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.PushDeliveryLogEntity;
import com.grun.calorietracker.entity.UserPushTokenEntity;
import com.grun.calorietracker.enums.PushDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface PushDeliveryLogRepository extends JpaRepository<PushDeliveryLogEntity, Long> {
    long deleteByPushToken(UserPushTokenEntity pushToken);
    long countByStatusAndCreatedAtAfter(PushDeliveryStatus status, LocalDateTime createdAt);
}
