package com.grun.calorietracker.dto;

import java.time.LocalDateTime;

public record AdminTestFeedbackScreenshotEventDto(
        String eventType, String outcome, Long reportedSizeBytes, Long actualSizeBytes,
        String contentType, String errorCode, String detail, LocalDateTime createdAt
) { }
