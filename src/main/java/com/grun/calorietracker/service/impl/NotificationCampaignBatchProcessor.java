package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.PushDeliveryResultDto;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.PushDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationCampaignBatchProcessor {
    private static final int BATCH_SIZE = 100;

    private final NotificationCampaignRepository campaignRepository;
    private final NotificationCampaignRecipientRepository recipientRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final PushDeliveryService pushDeliveryService;
    private final AdminNotificationCampaignServiceImpl campaignService;

    @Transactional
    public void dispatchBatch(Long campaignId) {
        NotificationCampaignEntity campaign = campaignRepository.findByIdForUpdate(campaignId).orElse(null);
        if (campaign == null || campaign.getStatus() == NotificationCampaignStatus.CANCELLED) {
            return;
        }
        if (campaign.getStatus() == NotificationCampaignStatus.SCHEDULED) {
            campaign.setStatus(NotificationCampaignStatus.PROCESSING);
            campaign.setStartedAt(LocalDateTime.now());
        }

        var users = userRepository.findAll(
                campaignService.audienceSpecification(campaign, campaign.getLastProcessedUserId()),
                PageRequest.of(0, BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id"))
        ).getContent();

        if (users.isEmpty()) {
            complete(campaign);
            return;
        }

        for (UserEntity user : users) {
            campaign.setLastProcessedUserId(user.getId());
            if (!recipientRepository.existsByCampaignIdAndUserId(campaign.getId(), user.getId())) {
                deliver(campaign, user);
            }
        }

        if (users.size() < BATCH_SIZE) {
            campaign.setStatus(NotificationCampaignStatus.COMPLETED);
            campaign.setCompletedAt(LocalDateTime.now());
        }
        campaign.setUpdatedAt(LocalDateTime.now());
        campaignRepository.save(campaign);
    }

    private void complete(NotificationCampaignEntity campaign) {
        campaign.setStatus(NotificationCampaignStatus.COMPLETED);
        campaign.setCompletedAt(LocalDateTime.now());
        campaign.setUpdatedAt(LocalDateTime.now());
        campaignRepository.save(campaign);
    }

    private void deliver(NotificationCampaignEntity campaign, UserEntity user) {
        LocalDateTime now = LocalDateTime.now();
        NotificationEntity notification = new NotificationEntity();
        notification.setUser(user);
        notification.setTitle(campaign.getTitle());
        notification.setMessage(campaign.getMessage());
        notification.setType(campaign.getCategory() == NotificationCampaignCategory.MARKETING ? "marketing" : "system_announcement");
        notification.setSeverity("INFO");
        notification.setSource("ADMIN_CAMPAIGN");
        notification.setTargetType("NOTIFICATION_CAMPAIGN");
        notification.setTargetId(campaign.getId().toString());
        notification.setTargetRoute(campaign.getTargetRoute());
        if (campaign.getTargetRoute() != null && !campaign.getTargetRoute().isBlank()) {
            notification.setPrimaryAction("VIEW_DETAILS");
        }
        notification.setCampaign(campaign);
        notification.setVisibleInApp(campaign.getChannel() != NotificationCampaignChannel.PUSH);
        notification.setIsRead(false);
        notification.setCreatedAt(now);
        notification = notificationRepository.save(notification);

        PushDeliveryResultDto push = new PushDeliveryResultDto(0, 0, 0, 0);
        if (campaign.getChannel() != NotificationCampaignChannel.IN_APP) {
            push = pushDeliveryService.deliver(notification);
            if (push.getAttempted() == 0 && push.getSent() == 0 && push.getFailed() == 0) {
                push.setSkipped(1);
            }
        }

        NotificationCampaignRecipientEntity recipient = new NotificationCampaignRecipientEntity();
        recipient.setCampaign(campaign);
        recipient.setUser(user);
        recipient.setNotification(notification);
        recipient.setCreatedAt(now);
        recipient.setProcessedAt(LocalDateTime.now());
        recipient.setPushAttempted(push.getAttempted());
        recipient.setPushSent(push.getSent());
        recipient.setPushSkipped(push.getSkipped());
        recipient.setPushFailed(push.getFailed());
        recipient.setStatus(campaign.getChannel() == NotificationCampaignChannel.PUSH
                        && push.getSent() == 0 && push.getFailed() > 0
                ? NotificationCampaignRecipientStatus.FAILED
                : NotificationCampaignRecipientStatus.DELIVERED);
        recipientRepository.save(recipient);

        campaign.setProcessedCount(campaign.getProcessedCount() + 1);
        if (Boolean.TRUE.equals(notification.getVisibleInApp())) {
            campaign.setInAppCount(campaign.getInAppCount() + 1);
        }
        campaign.setPushSentCount(campaign.getPushSentCount() + push.getSent());
        campaign.setPushSkippedCount(campaign.getPushSkippedCount() + push.getSkipped());
        campaign.setPushFailedCount(campaign.getPushFailedCount() + push.getFailed());
    }
}