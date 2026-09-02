package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserProductTourEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProductTourRepository extends JpaRepository<UserProductTourEntity, Long> {
    Optional<UserProductTourEntity> findByUserAndTourKeyAndTourVersion(
            UserEntity user, String tourKey, String tourVersion);
}
