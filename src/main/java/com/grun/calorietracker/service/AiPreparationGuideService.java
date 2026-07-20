package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiMealDraftRejectRequestDto;
import com.grun.calorietracker.dto.AiPreparationGuideGenerateRequestDto;
import com.grun.calorietracker.dto.AiPreparationGuideResponseDto;

public interface AiPreparationGuideService {
    AiPreparationGuideResponseDto generate(String email, Long planId, Long itemId,
                                           String idempotencyKey,
                                           AiPreparationGuideGenerateRequestDto request);
    AiPreparationGuideResponseDto reopen(String email, Long planId, Long itemId);
    AiPreparationGuideResponseDto regenerate(String email, Long planId, Long itemId,
                                             String idempotencyKey,
                                             AiPreparationGuideGenerateRequestDto request);
    void reject(String email, Long planId, Long itemId, Long requestId,
                AiMealDraftRejectRequestDto request);
}
