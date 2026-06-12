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
    long countByStartedAtAfter(LocalDateTime startedAt);
    long countByStatus(FastingSessionStatus status);
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

    @Query("""
            SELECT COUNT(DISTINCT session.user.id)
            FROM FastingSessionEntity session
            WHERE session.startedAt >= :startedAt
            """)
    long countDistinctUsersByStartedAtAfter(@Param("startedAt") LocalDateTime startedAt);

    @Query("""
            SELECT COALESCE(SUM(session.actualMinutes), 0)
            FROM FastingSessionEntity session
            WHERE session.startedAt >= :startedAt
              AND session.actualMinutes IS NOT NULL
            """)
    long sumActualMinutesByStartedAtAfter(@Param("startedAt") LocalDateTime startedAt);

    @Query("""
            SELECT session.fastingDate, COUNT(session), COUNT(DISTINCT session.user.id), COALESCE(SUM(session.actualMinutes), 0)
            FROM FastingSessionEntity session
            WHERE session.fastingDate BETWEEN :startDate AND :endDate
            GROUP BY session.fastingDate
            ORDER BY session.fastingDate
            """)
    List<Object[]> aggregateDailyFasting(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

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
            """)
    List<FastingSessionEntity> findReminderCandidateSessions(@Param("status") FastingSessionStatus status);
}
