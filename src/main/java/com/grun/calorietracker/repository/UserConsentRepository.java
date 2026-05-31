package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.UserConsentEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.LegalConsentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserConsentRepository extends JpaRepository<UserConsentEntity, Long> {
    List<UserConsentEntity> findByUserOrderByCreatedAtDesc(UserEntity user);

    List<UserConsentEntity> findByUserAndConsentTypeOrderByCreatedAtDesc(UserEntity user, LegalConsentType consentType);

    long countByUser(UserEntity user);
}
