package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.TestFeedbackPlatform;
import com.grun.calorietracker.enums.TestFeedbackStatus;
import com.grun.calorietracker.enums.TestFeedbackType;

import java.time.LocalDateTime;

public record AdminTestFeedbackDto(
        Long id, String userEmail, TestFeedbackType feedbackType, TestFeedbackStatus status,
        TestFeedbackPlatform platform, String route, String previousRoute, String description,
        String appVersion, String buildNumber, String easBuildId, String commitSha,
        String osVersion, String deviceModel, String languageTag, String marketRegion,
        Integer lastHttpStatus, Long lastHttpDurationMs, String lastCorrelationId,
        String networkState, boolean screenshotAvailable, LocalDateTime screenshotExpiresAt,
        String adminNote, String reviewedByEmail,
        LocalDateTime reviewedAt, LocalDateTime createdAt, LocalDateTime updatedAt
) { }
