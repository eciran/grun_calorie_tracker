package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.AiPhotoMealDraftRequestDto;
import com.grun.calorietracker.dto.AiSafetyResultDto;
import com.grun.calorietracker.dto.AiVoiceFoodDraftRequestDto;
import com.grun.calorietracker.enums.AiRequestType;

public interface AiMealDraftSafetyService {
    void validateVoiceRequest(AiVoiceFoodDraftRequestDto request);

    void validatePhotoRequest(AiPhotoMealDraftRequestDto request);

    AiSafetyResultDto reviewProviderResponse(AiMealDraftResponseDto response, AiRequestType requestType);
}
