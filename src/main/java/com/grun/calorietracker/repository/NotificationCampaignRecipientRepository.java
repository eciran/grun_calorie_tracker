package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.NotificationCampaignRecipientEntity;
import com.grun.calorietracker.enums.NotificationCampaignRecipientStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationCampaignRecipientRepository extends JpaRepository<NotificationCampaignRecipientEntity, Long> {
    boolean existsByCampaignIdAndUserId(Long campaignId, Long userId);

    Page<NotificationCampaignRecipientEntity> findByCampaignId(Long campaignId, Pageable pageable);

    Page<NotificationCampaignRecipientEntity> findByCampaignIdAndStatus(
            Long campaignId, NotificationCampaignRecipientStatus status, Pageable pageable);

    Optional<NotificationCampaignRecipientEntity> findByNotificationIdAndUserId(Long notificationId, Long userId);

    @Query("""
            select r.status, count(r)
            from NotificationCampaignRecipientEntity r
            where r.campaign.createdAt >= :from
            group by r.status
            order by r.status
            """)
    List<Object[]> countStatusesSince(@Param("from") LocalDateTime from);

    @Query("""
            select count(r) from NotificationCampaignRecipientEntity r
            where r.user.id = :userId
              and r.campaign.category = com.grun.calorietracker.enums.NotificationCampaignCategory.MARKETING
              and r.status <> com.grun.calorietracker.enums.NotificationCampaignRecipientStatus.SUPPRESSED
              and r.createdAt >= :createdAfter
            """)
    long countRecentMarketingDeliveries(@Param("userId") Long userId,
                                        @Param("createdAfter") LocalDateTime createdAfter);
}