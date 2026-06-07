package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiRecipeDraftConfirmRequestDto;
import com.grun.calorietracker.dto.AiRecipeDraftRequestDto;
import com.grun.calorietracker.dto.AiRecipeDraftResponseDto;
import com.grun.calorietracker.dto.RecipeDto;

public interface AiRecipeDraftService {
    AiRecipeDraftResponseDto createRecipeDraft(String email, AiRecipeDraftRequestDto request);

    RecipeDto confirmRecipeDraft(String email, Long requestId, AiRecipeDraftConfirmRequestDto request);
}
