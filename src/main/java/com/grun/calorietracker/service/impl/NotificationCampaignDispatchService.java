package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.enums.NotificationCampaignStatus;
import com.grun.calorietracker.repository.NotificationCampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationCampaignDispatchService {
    private final NotificationCampaignRepository campaignRepository;
    private final NotificationCampaignBatchProcessor batchProcessor;

    @Scheduled(fixedDelayString = "${grun.notifications.campaign-dispatch-interval-ms:30000}")
    public void dispatchDueCampaigns() {
        List<Long> dueIds = campaignRepository.findDueIds(
                List.of(NotificationCampaignStatus.SCHEDULED, NotificationCampaignStatus.PROCESSING),
                LocalDateTime.now(),
                PageRequest.of(0, 5)
        );
        dueIds.forEach(batchProcessor::dispatchBatch);
    }
}