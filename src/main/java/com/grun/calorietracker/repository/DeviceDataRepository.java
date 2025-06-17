package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.DeviceDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceDataRepository extends JpaRepository<DeviceDataEntity, Long> {
}
