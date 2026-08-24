package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.TestFeedbackCreateRequestDto;
import com.grun.calorietracker.dto.TestFeedbackSubmissionDto;

public interface TestFeedbackSubmissionService {
    TestFeedbackSubmissionDto submit(String userEmail, String environment, String idempotencyKey,
                                     TestFeedbackCreateRequestDto request);
}
