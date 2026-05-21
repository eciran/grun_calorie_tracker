package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.OnboardingCompleteRequestDto;
import com.grun.calorietracker.dto.OnboardingCompleteResponseDto;

public interface OnboardingService {
    OnboardingCompleteResponseDto completeOnboarding(OnboardingCompleteRequestDto request, String email);
}
