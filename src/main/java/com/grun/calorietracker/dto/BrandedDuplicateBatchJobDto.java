package com.grun.calorietracker.dto;

public record BrandedDuplicateBatchJobDto(
        Long id, String status, String query, Boolean safeOnly, Integer pageSize, Integer nextPage, Integer nextItemIndex,
        Long totalGroups, Long eligibleGroups, Long processedGroups, Long appliedGroups,
        Long skippedGroups, Long failedGroups, Boolean pauseRequested, Integer progressPercent,
        String requestedBy, String createdAt, String startedAt, String updatedAt, String completedAt,
        String lastError
) {
}
