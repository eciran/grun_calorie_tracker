package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AppliedPromoEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppliedPromoRepository extends JpaRepository<AppliedPromoEntity, Long> {
    long deleteByUser(UserEntity user);
}
