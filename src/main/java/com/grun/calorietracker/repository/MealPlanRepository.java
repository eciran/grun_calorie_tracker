package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.MealPlanEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.MealPlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MealPlanRepository extends JpaRepository<MealPlanEntity, Long> {
    long countByUser(UserEntity user);
    List<MealPlanEntity> findByUserOrderByStartDateDesc(UserEntity user);
    List<MealPlanEntity> findByUserAndStatusNotOrderByStartDateDesc(UserEntity user, MealPlanStatus status);
    Optional<MealPlanEntity> findByIdAndUser(Long id, UserEntity user);
    List<MealPlanEntity> findByUserAndStatus(UserEntity user, MealPlanStatus status);
    @Query("select p from MealPlanEntity p where p.user = :user and p.status = :status and p.startDate <= :date and p.endDate >= :date")
    Optional<MealPlanEntity> findActiveForDate(@Param("user") UserEntity user, @Param("status") MealPlanStatus status, @Param("date") LocalDate date);
    long deleteByUser(UserEntity user);
}
