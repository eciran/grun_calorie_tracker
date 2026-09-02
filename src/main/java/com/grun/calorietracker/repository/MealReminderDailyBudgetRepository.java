package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.MealReminderDailyBudgetEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface MealReminderDailyBudgetRepository extends JpaRepository<MealReminderDailyBudgetEntity, Long> {
    Optional<MealReminderDailyBudgetEntity> findByUserIdAndLocalDate(Long userId, LocalDate localDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select budget from MealReminderDailyBudgetEntity budget where budget.user.id = :userId and budget.localDate = :localDate")
    Optional<MealReminderDailyBudgetEntity> findForUpdate(
            @Param("userId") Long userId,
            @Param("localDate") LocalDate localDate);
}
