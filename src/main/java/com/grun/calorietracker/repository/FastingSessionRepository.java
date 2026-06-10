package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FastingSessionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FastingSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FastingSessionRepository extends JpaRepository<FastingSessionEntity, Long> {
    Optional<FastingSessionEntity> findTopByUserAndStatusOrderByStartedAtDesc(UserEntity user, FastingSessionStatus status);
    Optional<FastingSessionEntity> findByIdAndUser(Long id, UserEntity user);
    List<FastingSessionEntity> findByUserAndFastingDateOrderByStartedAtAsc(UserEntity user, LocalDate fastingDate);
    List<FastingSessionEntity> findByUserAndFastingDateBetweenOrderByFastingDateAscStartedAtAsc(
            UserEntity user,
            LocalDate startDate,
            LocalDate endDate
    );
    List<FastingSessionEntity> findByUserOrderByStartedAtAsc(UserEntity user);
    long countByUser(UserEntity user);
    long deleteByUser(UserEntity user);
    boolean existsByUserAndFastingDateAndStatusAndTargetReachedTrue(
            UserEntity user,
            LocalDate fastingDate,
            FastingSessionStatus status
    );
    Optional<FastingSessionEntity> findTopByUserOrderByStartedAtDesc(UserEntity user);
    List<FastingSessionEntity> findByUserAndStartedAtBetweenOrderByStartedAtAsc(
            UserEntity user,
            LocalDateTime start,
            LocalDateTime end
    );

    long countByUserAndStatusAndTargetReachedTrue(UserEntity user, FastingSessionStatus status);

    @Query(value = """
            SELECT COUNT(DISTINCT fasting_date)
            FROM fasting_sessions
            WHERE user_id = :userId
              AND status = :status
              AND target_reached = TRUE
            """, nativeQuery = true)
    long countDistinctCompletedTargetReachedDays(
            @Param("userId") Long userId,
            @Param("status") String status
    );

    @Query("""
            select session
            from FastingSessionEntity session
            join session.plan plan
            where session.status = :status
              and session.reminderSentAt is null
              and plan.reminderEnabled = true
              and session.targetEndAt between :now and :threshold
            """)
    List<FastingSessionEntity> findDueReminderSessions(
            @Param("status") FastingSessionStatus status,
            @Param("now") LocalDateTime now,
            @Param("threshold") LocalDateTime threshold
    );
}
