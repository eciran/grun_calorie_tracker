package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.OnboardingDraftEntity;
import com.grun.calorietracker.entity.UserEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

public interface OnboardingDraftRepository extends JpaRepository<OnboardingDraftEntity, Long> {

    Optional<OnboardingDraftEntity> findByUser(UserEntity user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from OnboardingDraftEntity d where d.user = :user")
    Optional<OnboardingDraftEntity> findByUserForUpdate(@Param("user") UserEntity user);

    long deleteByUser(UserEntity user);

    @Query("""
            select count(distinct draft.user.id)
            from OnboardingDraftEntity draft
            where draft.user.createdAt >= :registeredFrom
              and draft.user.createdAt < :registeredTo
              and draft.completedAt is not null
              and draft.completedAt < :completedBefore
            """)
    long countCompletedForRegistrationCohort(@Param("registeredFrom") Instant registeredFrom,
                                             @Param("registeredTo") Instant registeredTo,
                                             @Param("completedBefore") LocalDateTime completedBefore);
}
