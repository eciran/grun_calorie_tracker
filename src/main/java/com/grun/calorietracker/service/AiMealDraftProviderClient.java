package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.AiPhotoMealDraftRequestDto;
import com.grun.calorietracker.dto.AiVoiceFoodDraftRequestDto;
import com.grun.calorietracker.enums.AiProvider;

public interface AiMealDraftProviderClient {
    AiProvider provider();
    AiMealDraftResponseDto createVoiceFoodDraft(AiVoiceFoodDraftRequestDto request);
    AiMealDraftResponseDto createPhotoMealDraft(AiPhotoMealDraftRequestDto request);
}
