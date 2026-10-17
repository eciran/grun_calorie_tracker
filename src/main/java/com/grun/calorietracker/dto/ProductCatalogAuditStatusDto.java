package com.grun.calorietracker.dto;

import java.time.LocalDateTime;

public record ProductCatalogAuditStatusDto(
        Long scanRunId, String status, String rulesetVersion, long cursorId, long highWaterId,
        long rulesPassed, long reviewRequired, long retriesPending, long deadLetters,
        boolean leased, LocalDateTime leaseUntil, LocalDateTime lastCompletedAt) {
}
