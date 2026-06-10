package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.UserAchievementEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAchievementRepository extends JpaRepository<UserAchievementEntity, Long> {
    List<UserAchievementEntity> findByUser(UserEntity user);

    Optional<UserAchievementEntity> findByUserAndAchievementCode(UserEntity user, String achievementCode);

    long deleteByUser(UserEntity user);
}
