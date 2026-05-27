package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.HealthConnectionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.HealthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HealthConnectionRepository extends JpaRepository<HealthConnectionEntity, Long> {
    List<HealthConnectionEntity> findByUserOrderByProviderAsc(UserEntity user);
    Optional<HealthConnectionEntity> findByUserAndProvider(UserEntity user, HealthProvider provider);
}
