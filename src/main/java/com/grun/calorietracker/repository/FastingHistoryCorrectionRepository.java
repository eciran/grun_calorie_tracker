package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FastingHistoryCorrectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FastingHistoryCorrectionRepository extends JpaRepository<FastingHistoryCorrectionEntity, Long> {
    List<FastingHistoryCorrectionEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    long deleteByUserId(Long userId);
}