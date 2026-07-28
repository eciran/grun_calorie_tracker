package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.*;

public interface FastingScheduleExceptionRepository extends JpaRepository<FastingScheduleExceptionEntity, Long> {
    List<FastingScheduleExceptionEntity> findAllByUserIdOrderBySourceDateAsc(Long userId);
    Optional<FastingScheduleExceptionEntity> findByUserAndSourceDate(UserEntity user, LocalDate sourceDate);
    List<FastingScheduleExceptionEntity> findByUserAndSourceDateBetween(UserEntity user, LocalDate start, LocalDate end);
    List<FastingScheduleExceptionEntity> findByUserAndTargetDateBetween(UserEntity user, LocalDate start, LocalDate end);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from FastingScheduleExceptionEntity e where e.user = :user and e.sourceDate = :date")
    Optional<FastingScheduleExceptionEntity> findByUserAndSourceDateForUpdate(@Param("user") UserEntity user, @Param("date") LocalDate date);
}