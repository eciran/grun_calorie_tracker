package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserFitnessPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserFitnessPreferenceRepository extends JpaRepository<UserFitnessPreferenceEntity, Long> {
    Optional<UserFitnessPreferenceEntity> findByUser(UserEntity user);
    void deleteByUser(UserEntity user);
}
