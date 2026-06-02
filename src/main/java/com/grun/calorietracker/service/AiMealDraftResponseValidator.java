package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestType;

public interface AiMealDraftResponseValidator {
    AiMealDraftResponseDto validateAndNormalize(AiMealDraftResponseDto response,
                                                AiRequestType expectedType,
                                                AiProvider expectedProvider,
                                                String expectedModel);
}
