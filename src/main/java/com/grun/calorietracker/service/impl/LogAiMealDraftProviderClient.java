package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiMealDraftItemDto;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.AiPhotoMealDraftRequestDto;
import com.grun.calorietracker.dto.AiVoiceFoodDraftRequestDto;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.service.AiMealDraftProviderClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LogAiMealDraftProviderClient implements AiMealDraftProviderClient {

    private final AiProperties properties;

    @Override
    public AiProvider provider() {
        return AiProvider.LOG;
    }

    @Override
    public AiMealDraftResponseDto createVoiceFoodDraft(AiVoiceFoodDraftRequestDto request) {
        AiMealDraftResponseDto response = baseResponse(AiRequestType.VOICE_FOOD_LOG, request.getMealType(), request.getLogDate());
        response.setSummary("Draft generated from transcript. Configure a real AI provider before production use.");
        response.setItems(List.of(sampleItem(request.getTranscript())));
        return response;
    }

    @Override
    public AiMealDraftResponseDto createPhotoMealDraft(AiPhotoMealDraftRequestDto request) {
        AiMealDraftResponseDto response = baseResponse(AiRequestType.PHOTO_MEAL_LOG, request.getMealType(), request.getLogDate());
        response.setSummary("Draft generated from image reference. Configure a real vision provider before production use.");
        response.setItems(List.of(sampleItem(request.getUserNote() == null ? request.getImageReference() : request.getUserNote())));
        return response;
    }

    private AiMealDraftResponseDto baseResponse(AiRequestType type, String mealType, LocalDateTime logDate) {
        AiMealDraftResponseDto response = new AiMealDraftResponseDto();
        response.setRequestType(type);
        response.setProvider(AiProvider.LOG);
        response.setModel(properties.getModel());
        response.setStatus(AiRequestStatus.DRAFT_CREATED);
        response.setSuggestedMealType(mealType == null || mealType.isBlank() ? "SNACK" : mealType.trim().toUpperCase());
        response.setSuggestedLogDate(logDate == null ? LocalDateTime.now() : logDate);
        return response;
    }

    private AiMealDraftItemDto sampleItem(String sourceText) {
        AiMealDraftItemDto item = new AiMealDraftItemDto();
        item.setName(resolveSampleName(sourceText));
        item.setQuantity(100.0);
        item.setUnit("g");
        item.setEstimatedCalories(150.0);
        item.setEstimatedProtein(10.0);
        item.setEstimatedCarbs(15.0);
        item.setEstimatedFat(5.0);
        item.setConfidence(0.3);
        return item;
    }

    private String resolveSampleName(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return "Unverified meal item";
        }
        String normalized = sourceText.trim().replaceAll("\\s+", " ");
        return normalized.length() > 60 ? normalized.substring(0, 60) : normalized;
    }
}
