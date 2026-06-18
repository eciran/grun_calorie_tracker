package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiProviderSmokeResponseDto;
import com.grun.calorietracker.enums.AiRequestType;

public interface AiProviderSmokeService {
    AiProviderSmokeResponseDto smoke(AiRequestType requestType);
}
