package com.grun.calorietracker.dto;

import com.grun.calorietracker.entity.OwnerOperationalAlertEntity;
import java.time.Instant;

public record OwnerOperationalAlertDto(Long id, String category, String severity, String status,
        String titleEn, String titleTr, String messageEn, String messageTr, String targetPath,
        Long occurrenceCount, Instant firstOccurredAt, Instant lastOccurredAt, Integer attemptCount,
        Instant nextAttemptAt, Instant sentAt, String lastErrorType) {
    public static OwnerOperationalAlertDto from(OwnerOperationalAlertEntity value) {
        return new OwnerOperationalAlertDto(value.getId(), value.getCategory(), value.getSeverity(), value.getStatus(),
                value.getTitleEn(), value.getTitleTr(), value.getMessageEn(), value.getMessageTr(), value.getTargetPath(),
                value.getOccurrenceCount(), value.getFirstOccurredAt(), value.getLastOccurredAt(), value.getAttemptCount(),
                value.getNextAttemptAt(), value.getSentAt(), value.getLastErrorType());
    }
}
