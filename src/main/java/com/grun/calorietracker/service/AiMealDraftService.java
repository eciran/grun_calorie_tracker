package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.AiMealDraftConfirmRequestDto;
import com.grun.calorietracker.dto.AiMealDraftConfirmResponseDto;
import com.grun.calorietracker.dto.AiMealDraftRejectRequestDto;
import com.grun.calorietracker.dto.AiPhotoMealDraftRequestDto;
import com.grun.calorietracker.dto.AiRequestHistoryDto;
import com.grun.calorietracker.dto.AiVoiceFoodDraftRequestDto;

import java.util.List;
import java.util.UUID;

public interface AiMealDraftService {
    default AiMealDraftResponseDto createVoiceFoodDraft(String email, AiVoiceFoodDraftRequestDto request) {
        return createVoiceFoodDraft(email, UUID.randomUUID().toString(), request);
    }
    AiMealDraftResponseDto createVoiceFoodDraft(String email, String idempotencyKey, AiVoiceFoodDraftRequestDto request);
    default AiMealDraftResponseDto createPhotoMealDraft(String email, AiPhotoMealDraftRequestDto request) {
        return createPhotoMealDraft(email, UUID.randomUUID().toString(), request);
    }
    AiMealDraftResponseDto createPhotoMealDraft(String email, String idempotencyKey, AiPhotoMealDraftRequestDto request);
    AiMealDraftResponseDto getDraft(String email, Long requestId);
    AiMealDraftConfirmResponseDto confirmDraft(String email, Long requestId, AiMealDraftConfirmRequestDto request);
    AiRequestHistoryDto rejectDraft(String email, Long requestId, AiMealDraftRejectRequestDto request);
    List<AiRequestHistoryDto> listHistory(String email, int limit);
}
