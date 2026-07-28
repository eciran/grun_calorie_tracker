package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FastingHistoryCorrectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FastingHistoryCorrectionRepository extends JpaRepository<FastingHistoryCorrectionEntity, Long> {
    long deleteByUserId(Long userId);
}