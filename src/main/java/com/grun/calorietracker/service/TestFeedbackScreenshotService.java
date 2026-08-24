package com.grun.calorietracker.service;
import com.grun.calorietracker.dto.*;
public interface TestFeedbackScreenshotService {
 TestFeedbackScreenshotUploadDto authorize(String email,String environment,Long feedbackId,TestFeedbackScreenshotUploadRequestDto request);
 void complete(String email,String environment,Long feedbackId);
 TestFeedbackScreenshotReadDto authorizeAdminRead(Long feedbackId);
 int cleanupExpired();
}