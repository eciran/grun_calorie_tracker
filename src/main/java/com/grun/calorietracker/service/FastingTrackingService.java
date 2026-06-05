package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FastingDailySummaryDto;
import com.grun.calorietracker.dto.FastingPlanDto;
import com.grun.calorietracker.dto.FastingPlanRequestDto;
import com.grun.calorietracker.dto.FastingRangeSummaryDto;
import com.grun.calorietracker.dto.FastingSessionCancelRequestDto;
import com.grun.calorietracker.dto.FastingSessionDto;
import com.grun.calorietracker.dto.FastingSessionFinishRequestDto;
import com.grun.calorietracker.dto.FastingSessionStartRequestDto;

import java.time.LocalDate;

public interface FastingTrackingService {
    FastingPlanDto getPlan(String email);
    FastingPlanDto updatePlan(String email, FastingPlanRequestDto request);
    FastingSessionDto startSession(String email, FastingSessionStartRequestDto request);
    FastingSessionDto finishSession(String email, Long sessionId, FastingSessionFinishRequestDto request);
    FastingSessionDto cancelSession(String email, Long sessionId, FastingSessionCancelRequestDto request);
    FastingDailySummaryDto getDailySummary(String email, LocalDate date);
    FastingRangeSummaryDto getRangeSummary(String email, LocalDate startDate, LocalDate endDate);
    int createDueReminderNotifications();
}
