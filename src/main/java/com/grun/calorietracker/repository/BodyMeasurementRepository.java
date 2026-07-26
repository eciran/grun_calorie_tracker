package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.BodyMeasurementEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.HealthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BodyMeasurementRepository extends JpaRepository<BodyMeasurementEntity, Long> {
    Optional<BodyMeasurementEntity> findByIdAndUser(Long id, UserEntity user);
    Optional<BodyMeasurementEntity> findByUserAndProviderAndExternalId(UserEntity user, HealthProvider provider, String externalId);
    List<BodyMeasurementEntity> findByUserAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
            UserEntity user, LocalDateTime start, LocalDateTime end);
    Optional<BodyMeasurementEntity> findTopByUserOrderByRecordedAtDescIdDesc(UserEntity user);
    List<BodyMeasurementEntity> findTop2ByUserAndWeightKgIsNotNullOrderByRecordedAtDescIdDesc(UserEntity user);
    List<BodyMeasurementEntity> findTop2ByUserAndBodyFatPercentageIsNotNullOrderByRecordedAtDescIdDesc(UserEntity user);
    long countByUser(UserEntity user);
    long deleteByUser(UserEntity user);
}
