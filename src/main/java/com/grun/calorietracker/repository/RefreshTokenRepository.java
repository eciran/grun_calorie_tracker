package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.RefreshTokenEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    Optional<RefreshTokenEntity> findByTokenHashAndRevokedAtIsNullAndUsedAtIsNull(String tokenHash);

    List<RefreshTokenEntity> findByUserAndRevokedAtIsNullAndUsedAtIsNull(UserEntity user);
}
