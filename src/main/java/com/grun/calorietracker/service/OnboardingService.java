package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.OnboardingCompleteRequestDto;
import com.grun.calorietracker.dto.OnboardingCompleteResponseDto;
import com.grun.calorietracker.dto.OnboardingPreviewResponseDto;
import com.grun.calorietracker.dto.OnboardingStateDto;
import com.grun.calorietracker.dto.OnboardingStepUpdateRequestDto;
import com.grun.calorietracker.enums.OnboardingStep;

public interface OnboardingService {
    OnboardingStateDto getState(String email);
    OnboardingStateDto updateStep(OnboardingStep step, OnboardingStepUpdateRequestDto request, String email);
    OnboardingPreviewResponseDto preview(String email);
    OnboardingCompleteResponseDto completeOnboarding(String email);
    OnboardingCompleteResponseDto completeOnboarding(OnboardingCompleteRequestDto request, String email);
}