package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.WaterReminderSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WaterReminderSettingsRepository extends JpaRepository<WaterReminderSettingsEntity, Long> {
    Optional<WaterReminderSettingsEntity> findByUser(UserEntity user);
    List<WaterReminderSettingsEntity> findByEnabledTrue();

    long deleteByUser(UserEntity user);
}
