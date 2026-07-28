package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FastingScheduleExceptionAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FastingScheduleExceptionAuditRepository extends JpaRepository<FastingScheduleExceptionAuditEntity, Long> {}