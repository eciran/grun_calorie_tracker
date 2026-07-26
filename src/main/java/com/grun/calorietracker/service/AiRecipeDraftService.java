package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiRecipeDraftConfirmRequestDto;
import com.grun.calorietracker.dto.AiRecipeDraftRequestDto;
import com.grun.calorietracker.dto.AiRecipeDraftResponseDto;
import com.grun.calorietracker.dto.AiMealDraftRejectRequestDto;
import com.grun.calorietracker.dto.RecipeDto;

import java.util.UUID;

public interface AiRecipeDraftService {
    default AiRecipeDraftResponseDto createRecipeDraft(String email, AiRecipeDraftRequestDto request) {
        return createRecipeDraft(email, UUID.randomUUID().toString(), request);
    }

    AiRecipeDraftResponseDto createRecipeDraft(
            String email, String idempotencyKey, AiRecipeDraftRequestDto request);

    RecipeDto confirmRecipeDraft(String email, Long requestId, AiRecipeDraftConfirmRequestDto request);

    void rejectRecipeDraft(String email, Long requestId, AiMealDraftRejectRequestDto request);
}
