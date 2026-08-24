package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.enums.TestFeedbackPlatform;
import com.grun.calorietracker.enums.TestFeedbackStatus;
import com.grun.calorietracker.enums.TestFeedbackType;

public interface AdminTestFeedbackService {
    AdminTestFeedbackPageDto list(TestFeedbackStatus status, TestFeedbackType type,
                                  TestFeedbackPlatform platform, String route, int page, int size);
    AdminTestFeedbackDto detail(Long id);
    AdminTestFeedbackDto update(Long id, AdminTestFeedbackUpdateRequestDto request,
                                String adminEmail, String correlationId);
    AdminTestFeedbackAnalyticsDto analytics();
    byte[] exportCsv(TestFeedbackStatus status, TestFeedbackType type,
                     TestFeedbackPlatform platform, String route);
}
