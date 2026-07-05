package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiWorkoutPlanConfirmRequestDto;
import com.grun.calorietracker.dto.AiWorkoutPlanDraftRequestDto;
import com.grun.calorietracker.dto.AiWorkoutPlanDraftResponseDto;
import com.grun.calorietracker.dto.WorkoutPlanDto;

import java.util.List;

public interface AiWorkoutPlanService {
    AiWorkoutPlanDraftResponseDto createDraft(String email, AiWorkoutPlanDraftRequestDto request);
    WorkoutPlanDto confirmDraft(String email, Long requestId, AiWorkoutPlanConfirmRequestDto request);
    void rejectDraft(String email, Long requestId);
    List<WorkoutPlanDto> listActivePlans(String email);
    WorkoutPlanDto getPlan(String email, Long planId);
    void archivePlan(String email, Long planId);
}
