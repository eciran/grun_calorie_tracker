package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.UserDailyActivityEntity;
import com.grun.calorietracker.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserDailyActivityRepository extends JpaRepository<UserDailyActivityEntity, Long> {

    Optional<UserDailyActivityEntity> findByUserIdAndActivityDate(Long userId, LocalDate activityDate);

    long countByActivityDate(LocalDate activityDate);

    long countByActivityDateAndUserRole(LocalDate activityDate, UserRole role);

    @Query("""
            select count(distinct activity.user.id)
            from UserDailyActivityEntity activity
            where activity.activityDate between :fromDate and :toDate
            """)
    long countDistinctUsersBetween(@Param("fromDate") LocalDate fromDate,
                                   @Param("toDate") LocalDate toDate);

    @Query("""
            select count(distinct activity.user.id)
            from UserDailyActivityEntity activity
            where activity.activityDate between :fromDate and :toDate
              and activity.user.role = :role
            """)
    long countDistinctUsersBetweenByRole(@Param("fromDate") LocalDate fromDate,
                                         @Param("toDate") LocalDate toDate,
                                         @Param("role") UserRole role);

    @Query("""
            select activity.activityDate as activityDate, count(activity.id) as activeUsers
            from UserDailyActivityEntity activity
            where activity.activityDate between :fromDate and :toDate
            group by activity.activityDate
            order by activity.activityDate
            """)
    List<UserActivityDailyCountProjection> countDailyActiveUsers(@Param("fromDate") LocalDate fromDate,
                                                                 @Param("toDate") LocalDate toDate);

    @Query("""
            select activity.activityDate as activityDate, count(activity.id) as activeUsers
            from UserDailyActivityEntity activity
            where activity.activityDate between :fromDate and :toDate
              and activity.user.role = :role
            group by activity.activityDate
            order by activity.activityDate
            """)
    List<UserActivityDailyCountProjection> countDailyActiveUsersByRole(@Param("fromDate") LocalDate fromDate,
                                                                       @Param("toDate") LocalDate toDate,
                                                                       @Param("role") UserRole role);

    @Modifying
    long deleteByActivityDateBefore(LocalDate cutoffDate);
}
