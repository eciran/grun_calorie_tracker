package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.NotificationPlatformPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface NotificationPlatformPolicyRepository extends JpaRepository<NotificationPlatformPolicyEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NotificationPlatformPolicyEntity> findWithLockById(Long id);
}
