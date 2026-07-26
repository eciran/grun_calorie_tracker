package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.RuntimeOperationRecordType;
import com.grun.calorietracker.enums.RuntimeOperationStatus;

import java.time.LocalDateTime;

public record AdminRuntimeOperationRecordDto(
        Long id,
        RuntimeOperationRecordType recordType,
        RuntimeOperationStatus status,
        String operationKey,
        String title,
        String summary,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime nextRunAt,
        Boolean retryable,
        Integer retryCount,
        Long parentRecordId,
        String createdBy,
        LocalDateTime createdAt
) {
}
