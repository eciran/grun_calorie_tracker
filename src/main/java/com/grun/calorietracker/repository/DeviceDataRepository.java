package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.DeviceDataEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.HealthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeviceDataRepository extends JpaRepository<DeviceDataEntity, Long> {
    long countByUser(UserEntity user);
    List<DeviceDataEntity> findByUserOrderByRecordedAtAsc(UserEntity user);
    Optional<DeviceDataEntity> findByUserAndProviderAndExternalId(UserEntity user, HealthProvider provider, String externalId);

    Optional<DeviceDataEntity> findByUserAndProviderAndExternalIdIsNullAndRecordedAt(
            UserEntity user,
            HealthProvider provider,
            LocalDateTime recordedAt
    );

    List<DeviceDataEntity> findByUserAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
            UserEntity user,
            LocalDateTime start,
            LocalDateTime end
    );

    long deleteByUserAndProvider(UserEntity user, HealthProvider provider);

    long deleteByUser(UserEntity user);

    List<DeviceDataEntity> findByUserAndProviderAndRecordedAtBetweenOrderByRecordedAtAsc(
            UserEntity user,
            HealthProvider provider,
            LocalDateTime start,
            LocalDateTime end
    );
}
