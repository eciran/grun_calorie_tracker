package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.MealReminderDryRunDecisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface MealReminderDryRunDecisionRepository
        extends JpaRepository<MealReminderDryRunDecisionEntity, Long> {
    long deleteByExpiresAtLessThanEqual(Instant now);
    List<MealReminderDryRunDecisionEntity> findAllByOrderByEvaluatedAtDesc(Pageable pageable);
}
