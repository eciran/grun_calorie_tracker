package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiWorkoutPlanConfirmRequestDto;
import com.grun.calorietracker.dto.AiMealDraftRejectRequestDto;
import com.grun.calorietracker.dto.AiWorkoutPlanDraftRequestDto;
import com.grun.calorietracker.dto.AiWorkoutPlanDraftResponseDto;
import com.grun.calorietracker.dto.AiWorkoutPlanCreditEstimateDto;
import com.grun.calorietracker.dto.WorkoutPlanDto;
import com.grun.calorietracker.dto.WorkoutPlanScheduleUpdateRequestDto;

import java.util.List;
import java.util.UUID;

public interface AiWorkoutPlanService {
    AiWorkoutPlanCreditEstimateDto estimateCreditCost(String email, int daysPerWeek, int minutesPerSession);
    default AiWorkoutPlanDraftResponseDto createDraft(String email, AiWorkoutPlanDraftRequestDto request) {
        return createDraft(email, UUID.randomUUID().toString(), request);
    }
    AiWorkoutPlanDraftResponseDto createDraft(
            String email, String idempotencyKey, AiWorkoutPlanDraftRequestDto request);
    WorkoutPlanDto confirmDraft(String email, Long requestId, AiWorkoutPlanConfirmRequestDto request);
    default void rejectDraft(String email, Long requestId) {
        rejectDraft(email, requestId, null);
    }
    void rejectDraft(String email, Long requestId, AiMealDraftRejectRequestDto request);
    List<WorkoutPlanDto> listActivePlans(String email);
    List<WorkoutPlanDto> listAllPlans(String email);
    WorkoutPlanDto getPlan(String email, Long planId);
    WorkoutPlanDto updateSchedule(String email, Long planId, WorkoutPlanScheduleUpdateRequestDto request);
    void archivePlan(String email, Long planId);
}
