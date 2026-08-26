package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.time.LocalDate;

public interface GoalRepository extends JpaRepository<UserGoalEntity, Long> {
    @Query("select g from UserGoalEntity g where g.user = :user and g.effectiveUntil is null order by g.effectiveFrom desc")
    Optional<UserGoalEntity> findByUser(@Param("user") UserEntity user);
    List<UserGoalEntity> findAllByUserOrderByEffectiveFromDesc(UserEntity user);
    Optional<UserGoalEntity> findFirstByUserAndEffectiveLocalDateLessThanEqualOrderByEffectiveFromDesc(
            UserEntity user, LocalDate effectiveLocalDate);
    long deleteByUser(UserEntity user);
}
