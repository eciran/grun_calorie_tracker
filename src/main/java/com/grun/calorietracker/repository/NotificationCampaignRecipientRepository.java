package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.NotificationCampaignRecipientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationCampaignRecipientRepository extends JpaRepository<NotificationCampaignRecipientEntity, Long> {
    boolean existsByCampaignIdAndUserId(Long campaignId, Long userId);
}