package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.WaterLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WaterLogRepository extends JpaRepository<WaterLogEntity, Long> {
    List<WaterLogEntity> findByUserAndLogDateOrderByLoggedAtAsc(UserEntity user, LocalDate logDate);
    List<WaterLogEntity> findByUserOrderByLoggedAtAsc(UserEntity user);
    Optional<WaterLogEntity> findByIdAndUser(Long id, UserEntity user);
    Optional<WaterLogEntity> findTopByUserOrderByLoggedAtDesc(UserEntity user);
    long countByUser(UserEntity user);
    long deleteByUser(UserEntity user);

    @Query("""
            SELECT COALESCE(SUM(w.amountMl), 0)
            FROM WaterLogEntity w
            WHERE w.user = :user
              AND w.logDate = :logDate
            """)
    Long sumAmountMlByUserAndLogDate(@Param("user") UserEntity user, @Param("logDate") LocalDate logDate);

    List<WaterLogEntity> findByUserAndLoggedAtBetweenOrderByLoggedAtAsc(
            UserEntity user,
            LocalDateTime start,
            LocalDateTime end
    );
}
