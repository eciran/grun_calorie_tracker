package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.NotificationCampaignEntity;
import com.grun.calorietracker.enums.NotificationCampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationCampaignRepository extends JpaRepository<NotificationCampaignEntity, Long> {
    Page<NotificationCampaignEntity> findByStatus(NotificationCampaignStatus status, Pageable pageable);

    @Query("""
            select c.id from NotificationCampaignEntity c
            where c.status in :statuses
              and c.scheduledAt <= :now
            order by c.scheduledAt, c.id
            """)
    List<Long> findDueIds(@Param("statuses") List<NotificationCampaignStatus> statuses, @Param("now") LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from NotificationCampaignEntity c where c.id = :id")
    Optional<NotificationCampaignEntity> findByIdForUpdate(Long id);

    @Modifying
    @Query("update NotificationCampaignEntity c set c.openedCount = c.openedCount + 1 where c.id = :id")
    void incrementOpened(@Param("id") Long id);

    @Modifying
    @Query("update NotificationCampaignEntity c set c.clickedCount = c.clickedCount + 1 where c.id = :id")
    void incrementClicked(@Param("id") Long id);

    @Modifying
    @Query("update NotificationCampaignEntity c set c.dismissedCount = c.dismissedCount + 1 where c.id = :id")
    void incrementDismissed(@Param("id") Long id);

    @Modifying
    @Query("update NotificationCampaignEntity c set c.convertedCount = c.convertedCount + 1 where c.id = :id")
    void incrementConverted(@Param("id") Long id);
}