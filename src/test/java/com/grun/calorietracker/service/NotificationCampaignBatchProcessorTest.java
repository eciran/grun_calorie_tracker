package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.PushDeliveryResultDto;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.impl.AdminNotificationCampaignServiceImpl;
import com.grun.calorietracker.service.impl.NotificationCampaignBatchProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationCampaignBatchProcessorTest {
    @Mock NotificationCampaignRepository campaignRepository;
    @Mock NotificationCampaignRecipientRepository recipientRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock UserRepository userRepository;
    @Mock PushDeliveryService pushDeliveryService;
    @Mock AdminNotificationCampaignServiceImpl campaignService;
    @InjectMocks NotificationCampaignBatchProcessor processor;

    @Test
    void dispatchBatch_deliversOnceAndCompletesSmallBatch() {
        NotificationCampaignEntity campaign = new NotificationCampaignEntity();
        campaign.setId(7L);
        campaign.setTitle("Service update");
        campaign.setMessage("The service has been updated.");
        campaign.setCategory(NotificationCampaignCategory.SYSTEM);
        campaign.setChannel(NotificationCampaignChannel.IN_APP_AND_PUSH);
        campaign.setStatus(NotificationCampaignStatus.SCHEDULED);
        campaign.setLastProcessedUserId(0L);
        campaign.setProcessedCount(0L);
        campaign.setInAppCount(0L);
        campaign.setPushSentCount(0L);
        campaign.setPushSkippedCount(0L);
        campaign.setPushFailedCount(0L);
        campaign.setScheduledAt(LocalDateTime.now());

        UserEntity user = new UserEntity();
        user.setId(12L);
        user.setRole(UserRole.STANDARD);
        user.setAccountEnabled(true);
        user.setAccountLocked(false);
        user.setPushNotificationsEnabled(true);

        Specification<UserEntity> specification = (root, query, cb) -> cb.conjunction();
        when(campaignRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(campaign));
        when(campaignService.audienceSpecification(campaign, 0L)).thenReturn(specification);
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));
        when(recipientRepository.existsByCampaignIdAndUserId(7L, 12L)).thenReturn(false);
        when(notificationRepository.save(any(NotificationEntity.class))).thenAnswer(invocation -> {
            NotificationEntity notification = invocation.getArgument(0);
            notification.setId(21L);
            return notification;
        });
        when(pushDeliveryService.deliver(any(NotificationEntity.class)))
                .thenReturn(new PushDeliveryResultDto(1, 1, 0, 0));

        processor.dispatchBatch(7L);

        assertEquals(NotificationCampaignStatus.COMPLETED, campaign.getStatus());
        assertEquals(1L, campaign.getProcessedCount());
        assertEquals(1L, campaign.getInAppCount());
        assertEquals(1L, campaign.getPushSentCount());
        verify(recipientRepository).save(argThat(recipient ->
                recipient.getStatus() == NotificationCampaignRecipientStatus.DELIVERED
                        && recipient.getUser().getId().equals(12L)
                        && recipient.getNotification().getId().equals(21L)));
        verify(campaignRepository).save(campaign);
    }

    @Test
    void dispatchBatch_suppressesMarketingRecipientAboveFrequencyCap() {
        NotificationCampaignEntity campaign = new NotificationCampaignEntity();
        campaign.setId(8L);
        campaign.setCategory(NotificationCampaignCategory.MARKETING);
        campaign.setChannel(NotificationCampaignChannel.IN_APP_AND_PUSH);
        campaign.setStatus(NotificationCampaignStatus.SCHEDULED);
        campaign.setLastProcessedUserId(0L);
        campaign.setProcessedCount(0L);
        campaign.setInAppCount(0L);
        campaign.setPushSentCount(0L);
        campaign.setPushSkippedCount(0L);
        campaign.setPushFailedCount(0L);
        campaign.setSuppressedCount(0L);
        campaign.setFrequencyCapHours(24);
        campaign.setFrequencyCapMax(3);

        UserEntity user = new UserEntity();
        user.setId(15L);
        Specification<UserEntity> specification = (root, query, cb) -> cb.conjunction();
        when(campaignRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(campaign));
        when(campaignService.audienceSpecification(campaign, 0L)).thenReturn(specification);
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));
        when(recipientRepository.existsByCampaignIdAndUserId(8L, 15L)).thenReturn(false);
        when(recipientRepository.countRecentMarketingDeliveries(eq(15L), any(LocalDateTime.class))).thenReturn(3L);

        processor.dispatchBatch(8L);

        assertEquals(1L, campaign.getSuppressedCount());
        assertEquals(1L, campaign.getProcessedCount());
        verify(recipientRepository).save(argThat(recipient ->
                recipient.getStatus() == NotificationCampaignRecipientStatus.SUPPRESSED
                        && "MARKETING_FREQUENCY_CAP".equals(recipient.getSuppressionReason())));
        verify(notificationRepository, never()).save(any());
        verify(pushDeliveryService, never()).deliver(any());
    }

    @Test
    void dispatchBatch_skipsExistingRecipient() {
        NotificationCampaignEntity campaign = new NotificationCampaignEntity();
        campaign.setId(7L);
        campaign.setStatus(NotificationCampaignStatus.PROCESSING);
        campaign.setLastProcessedUserId(0L);
        campaign.setProcessedCount(1L);
        campaign.setInAppCount(1L);
        campaign.setPushSentCount(0L);
        campaign.setPushSkippedCount(0L);
        campaign.setPushFailedCount(0L);

        UserEntity user = new UserEntity();
        user.setId(12L);
        Specification<UserEntity> specification = (root, query, cb) -> cb.conjunction();
        when(campaignRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(campaign));
        when(campaignService.audienceSpecification(campaign, 0L)).thenReturn(specification);
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));
        when(recipientRepository.existsByCampaignIdAndUserId(7L, 12L)).thenReturn(true);

        processor.dispatchBatch(7L);

        assertEquals(1L, campaign.getProcessedCount());
        verify(notificationRepository, never()).save(any());
        verify(pushDeliveryService, never()).deliver(any());
    }
}