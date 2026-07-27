package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminNotificationCampaignRequestDto;
import com.grun.calorietracker.entity.NotificationCampaignEntity;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.NotificationCampaignRepository;
import com.grun.calorietracker.repository.NotificationCampaignRecipientRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.AdminNotificationCampaignServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminNotificationCampaignServiceImplTest {
    @Mock
    private NotificationCampaignRepository campaignRepository;
    @Mock
    private NotificationCampaignRecipientRepository recipientRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminAuditService adminAuditService;
    @InjectMocks
    private AdminNotificationCampaignServiceImpl service;

    @Test
    void create_storesDraftAndRecordsAudit() {
        when(campaignRepository.save(any(NotificationCampaignEntity.class))).thenAnswer(invocation -> {
            NotificationCampaignEntity entity = invocation.getArgument(0);
            entity.setId(11L);
            return entity;
        });

        var result = service.create(request(), "admin@grun.local", "cid-1");

        assertEquals(11L, result.getId());
        assertEquals(NotificationCampaignStatus.DRAFT, result.getStatus());
        assertEquals(NotificationCampaignCategory.MARKETING, result.getCategory());
        verify(adminAuditService).record(
                eq("admin@grun.local"),
                eq(AdminAuditActionType.NOTIFICATION_CAMPAIGN_CREATE),
                eq(AdminAuditTargetType.NOTIFICATION_CAMPAIGN),
                eq("11"),
                isNull(),
                any(),
                eq("cid-1")
        );
    }

    @Test
    void schedule_capturesAudienceAndQueuesCampaign() {
        NotificationCampaignEntity campaign = campaign();
        when(campaignRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(campaign));
        when(userRepository.count(any(Specification.class))).thenReturn(42L);
        when(campaignRepository.save(campaign)).thenReturn(campaign);

        var result = service.schedule(11L, LocalDateTime.now().plusHours(1), "admin@grun.local", "cid-2");

        assertEquals(NotificationCampaignStatus.SCHEDULED, result.getStatus());
        assertEquals(42L, result.getEstimatedAudience());
        assertNotNull(result.getScheduledAt());
    }

    @Test
    void update_rejectsNonDraftCampaign() {
        NotificationCampaignEntity campaign = campaign();
        campaign.setStatus(NotificationCampaignStatus.PROCESSING);
        when(campaignRepository.findById(11L)).thenReturn(Optional.of(campaign));

        assertThrows(IllegalArgumentException.class,
                () -> service.update(11L, request(), "admin@grun.local", "cid-3"));
        verify(campaignRepository, never()).save(any());
    }


    @Test
    void summary_returnsPrivacySafeAggregateMetrics() {
        when(campaignRepository.summarizeSince(any(LocalDateTime.class))).thenReturn(List.<Object[]>of(new Object[]{
                4L, 120L, 100L, 50L, 20L, 5L, 7L, 80L, 10L, 2L
        }));
        when(campaignRepository.countStatusesSince(any(LocalDateTime.class))).thenReturn(List.of(
                new Object[]{NotificationCampaignStatus.COMPLETED, 3L},
                new Object[]{NotificationCampaignStatus.FAILED, 1L}
        ));
        when(recipientRepository.countStatusesSince(any(LocalDateTime.class))).thenReturn(List.of(
                new Object[]{NotificationCampaignRecipientStatus.DELIVERED, 90L},
                new Object[]{NotificationCampaignRecipientStatus.SUPPRESSED, 8L},
                new Object[]{NotificationCampaignRecipientStatus.FAILED, 2L}
        ));

        var result = service.summary(31);

        assertEquals(31, result.getWindowDays());
        assertEquals(4L, result.getCampaignCount());
        assertEquals(90L, result.getDeliveredCount());
        assertEquals(8L, result.getSuppressedCount());
        assertEquals(2L, result.getFailedRecipientCount());
        assertEquals(50L, result.getOpenedCount());
        assertEquals(2, result.getCampaignStatuses().size());
        assertEquals(3, result.getRecipientStatuses().size());
    }

    private AdminNotificationCampaignRequestDto request() {
        AdminNotificationCampaignRequestDto request = new AdminNotificationCampaignRequestDto();
        request.setName("Plus launch");
        request.setTitle("A new plan is available");
        request.setMessage("Review the new plan details.");
        request.setCategory(NotificationCampaignCategory.MARKETING);
        request.setChannel(NotificationCampaignChannel.IN_APP_AND_PUSH);
        request.setTargetPlan(SubscriptionPlan.FREE);
        request.setTargetRegion(MarketRegion.UK_IE);
        request.setTargetLanguage(PreferredLanguage.EN);
        return request;
    }

    private NotificationCampaignEntity campaign() {
        NotificationCampaignEntity campaign = new NotificationCampaignEntity();
        campaign.setId(11L);
        campaign.setName("Plus launch");
        campaign.setTitle("A new plan is available");
        campaign.setMessage("Review the new plan details.");
        campaign.setCategory(NotificationCampaignCategory.MARKETING);
        campaign.setChannel(NotificationCampaignChannel.IN_APP_AND_PUSH);
        campaign.setStatus(NotificationCampaignStatus.DRAFT);
        campaign.setCreatedBy("admin@grun.local");
        campaign.setCreatedAt(LocalDateTime.now());
        campaign.setUpdatedAt(LocalDateTime.now());
        return campaign;
    }
}