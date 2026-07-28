package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;

public interface AdvancedFastingDiaryConflictService {
    FastingDiaryConflictDto evaluate(String email, FastingDiaryConflictEvaluateRequestDto request);
    FastingDiaryConflictResolutionDto resolve(String email, FastingDiaryConflictResolveRequestDto request);
}