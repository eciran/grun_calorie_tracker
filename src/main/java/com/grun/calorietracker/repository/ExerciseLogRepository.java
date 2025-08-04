package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ExerciseLogsEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExerciseLogRepository extends JpaRepository<ExerciseLogsEntity, Long> {
    List<ExerciseLogsEntity> findByUser(UserEntity user);
    List<ExerciseLogsEntity> findByUserAndLogDateBetween(UserEntity user, LocalDateTime start, LocalDateTime end);
    Optional<ExerciseLogsEntity> findByIdAndUser(Long id, UserEntity user);
    List<ExerciseLogsEntity> findByUserAndSource(UserEntity user, String source);
    Optional<ExerciseLogsEntity> findByExternalIdAndUser(String externalId, UserEntity user);
}
