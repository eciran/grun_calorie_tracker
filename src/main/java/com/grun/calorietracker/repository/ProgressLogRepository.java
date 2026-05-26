package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ProgressLogEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProgressLogRepository extends JpaRepository<ProgressLogEntity, Long> {

    List<ProgressLogEntity> findByUserOrderByLogDateAsc(UserEntity user);

    List<ProgressLogEntity> findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
            UserEntity user,
            LocalDateTime start,
            LocalDateTime end
    );

    Optional<ProgressLogEntity> findByIdAndUser(Long id, UserEntity user);

    Optional<ProgressLogEntity> findTopByUserOrderByLogDateDesc(UserEntity user);
}
