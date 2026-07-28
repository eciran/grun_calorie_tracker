package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FastingScheduleExceptionAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FastingScheduleExceptionAuditRepository extends JpaRepository<FastingScheduleExceptionAuditEntity, Long> {
    List<FastingScheduleExceptionAuditEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}