package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AdvancedFastingReminderSettingsEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdvancedFastingReminderSettingsRepository extends JpaRepository<AdvancedFastingReminderSettingsEntity, Long> {
    Optional<AdvancedFastingReminderSettingsEntity> findByUser(UserEntity user);
    long deleteByUser(UserEntity user);
}