package com.grun.calorietracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminNotificationCampaignSummaryDto {
    private Integer windowDays;
    private LocalDateTime from;
    private LocalDateTime to;
    private Long campaignCount;
    private Long estimatedAudience;
    private Long processedCount;
    private Long deliveredCount;
    private Long suppressedCount;
    private Long failedRecipientCount;
    private Long openedCount;
    private Long clickedCount;
    private Long dismissedCount;
    private Long convertedCount;
    private Long pushSentCount;
    private Long pushSkippedCount;
    private Long pushFailedCount;
    private List<CountMetric> campaignStatuses;
    private List<CountMetric> recipientStatuses;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CountMetric {
        private String name;
        private Long count;
    }
}