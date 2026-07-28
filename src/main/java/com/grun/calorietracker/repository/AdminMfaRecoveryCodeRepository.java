package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AdminMfaRecoveryCodeEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminMfaRecoveryCodeRepository extends JpaRepository<AdminMfaRecoveryCodeEntity, Long> {
    List<AdminMfaRecoveryCodeEntity> findByUserAndUsedAtIsNull(UserEntity user);
    long countByUserAndUsedAtIsNull(UserEntity user);
    void deleteByUser(UserEntity user);
}