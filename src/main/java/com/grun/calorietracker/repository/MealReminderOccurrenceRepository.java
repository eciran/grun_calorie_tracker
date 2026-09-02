package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.MealReminderOccurrenceEntity;
import com.grun.calorietracker.service.reminder.MealReminderContract;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MealReminderOccurrenceRepository extends JpaRepository<MealReminderOccurrenceEntity, Long> {
    Optional<MealReminderOccurrenceEntity> findByUserIdAndLocalDateAndSlot(
            Long userId, LocalDate localDate, MealReminderContract.Slot slot);

    @Query("""
            select occurrence from MealReminderOccurrenceEntity occurrence
            join fetch occurrence.notification notification
            where notification.id = :notificationId and occurrence.user.id = :userId
            """)
    Optional<MealReminderOccurrenceEntity> findByNotificationIdAndUserId(
            @Param("notificationId") Long notificationId, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select occurrence from MealReminderOccurrenceEntity occurrence where occurrence.id = :id")
    Optional<MealReminderOccurrenceEntity> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select count(occurrence) from MealReminderOccurrenceEntity occurrence
            where occurrence.user.id = :userId
              and occurrence.reservationActive = true
              and occurrence.createdAt >= :fromInclusive
              and (:excludedId is null or occurrence.id <> :excludedId)
            """)
    long countRollingReservations(
            @Param("userId") Long userId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("excludedId") Long excludedId);

    @Query("""
            select occurrence from MealReminderOccurrenceEntity occurrence
            where occurrence.user.id = :userId
              and occurrence.reservationActive = true
              and (:excludedId is null or occurrence.id <> :excludedId)
            order by occurrence.createdAt desc
            """)
    List<MealReminderOccurrenceEntity> findRecentActive(
            @Param("userId") Long userId,
            @Param("excludedId") Long excludedId,
            org.springframework.data.domain.Pageable pageable);
}
