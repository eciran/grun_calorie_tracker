package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.TestFeedbackPlatform;
import com.grun.calorietracker.enums.TestFeedbackStatus;
import com.grun.calorietracker.enums.TestFeedbackType;

import java.time.LocalDateTime;

public record TestFeedbackSubmissionDto(
        Long id,
        TestFeedbackType feedbackType,
        TestFeedbackStatus status,
        TestFeedbackPlatform platform,
        String route,
        LocalDateTime createdAt,
        boolean duplicate
) { }
