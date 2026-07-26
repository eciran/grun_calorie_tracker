package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.OnboardingDraftEntity;
import com.grun.calorietracker.entity.UserEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OnboardingDraftRepository extends JpaRepository<OnboardingDraftEntity, Long> {

    Optional<OnboardingDraftEntity> findByUser(UserEntity user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from OnboardingDraftEntity d where d.user = :user")
    Optional<OnboardingDraftEntity> findByUserForUpdate(@Param("user") UserEntity user);

    long deleteByUser(UserEntity user);
}
