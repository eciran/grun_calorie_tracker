package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.AiMealDraftConfirmRequestDto;
import com.grun.calorietracker.dto.AiMealDraftConfirmResponseDto;
import com.grun.calorietracker.dto.AiMealDraftRejectRequestDto;
import com.grun.calorietracker.dto.AiPhotoMealDraftRequestDto;
import com.grun.calorietracker.dto.AiRequestHistoryDto;
import com.grun.calorietracker.dto.AiVoiceFoodDraftRequestDto;

import java.util.List;

public interface AiMealDraftService {
    AiMealDraftResponseDto createVoiceFoodDraft(String email, AiVoiceFoodDraftRequestDto request);
    AiMealDraftResponseDto createPhotoMealDraft(String email, AiPhotoMealDraftRequestDto request);
    AiMealDraftConfirmResponseDto confirmDraft(String email, Long requestId, AiMealDraftConfirmRequestDto request);
    AiRequestHistoryDto rejectDraft(String email, Long requestId, AiMealDraftRejectRequestDto request);
    List<AiRequestHistoryDto> listHistory(String email, int limit);
}
