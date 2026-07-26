package com.grun.calorietracker.dto;

import java.time.LocalDateTime;

public record AdminCatalogImportJobDto(
        String jobKey,
        String catalogType,
        String source,
        String triggerType,
        String region,
        String status,
        long processedItems,
        long issueItems,
        String licenseEvidence,
        boolean retryable,
        String failureDetail,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
}
