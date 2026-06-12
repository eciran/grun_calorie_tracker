package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserPushTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPushTokenRepository extends JpaRepository<UserPushTokenEntity, Long> {
    List<UserPushTokenEntity> findByUserAndEnabledTrue(UserEntity user);
    List<UserPushTokenEntity> findByUserOrderByLastSeenAtDesc(UserEntity user);
    long countByEnabledTrue();
    long countByProviderAndEnabledTrue(com.grun.calorietracker.enums.PushProvider provider);
    Optional<UserPushTokenEntity> findByTokenHash(String tokenHash);
    Optional<UserPushTokenEntity> findByIdAndUser(Long id, UserEntity user);
    long deleteByUser(UserEntity user);
}
