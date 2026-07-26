package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.SleepSessionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.HealthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SleepSessionRepository extends JpaRepository<SleepSessionEntity, Long> {
    Optional<SleepSessionEntity> findByUserAndProviderAndExternalId(
            UserEntity user, HealthProvider provider, String externalId);

    boolean existsByUserAndProviderAndStartedAtAndEndedAt(
            UserEntity user, HealthProvider provider, Instant startedAt, Instant endedAt);

    Optional<SleepSessionEntity> findByIdAndUser(Long id, UserEntity user);

    List<SleepSessionEntity> findByUserAndSleepDateBetweenOrderByStartedAtAsc(
            UserEntity user, LocalDate startDate, LocalDate endDate);

    List<SleepSessionEntity> findByUserOrderByStartedAtAsc(UserEntity user);

    long countByUser(UserEntity user);

    long deleteByUserAndProvider(UserEntity user, HealthProvider provider);

    long deleteByUser(UserEntity user);
}
