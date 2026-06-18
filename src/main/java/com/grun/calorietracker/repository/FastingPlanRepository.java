package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FastingPlanEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FastingPlanRepository extends JpaRepository<FastingPlanEntity, Long> {
    Optional<FastingPlanEntity> findByUser(UserEntity user);
    long countByActiveTrue();
    long countByReminderEnabledTrue();
    long deleteByUser(UserEntity user);
}
