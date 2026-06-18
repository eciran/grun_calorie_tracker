package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.AiPhotoMealDraftRequestDto;
import com.grun.calorietracker.dto.AiProviderSmokeResponseDto;
import com.grun.calorietracker.dto.AiVoiceFoodDraftRequestDto;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.service.AiMealDraftProviderClient;
import com.grun.calorietracker.service.AiProviderConfigurationValidator;
import com.grun.calorietracker.service.AiProviderSmokeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiProviderSmokeServiceImpl implements AiProviderSmokeService {

    private final AiProperties properties;
    private final AiProviderConfigurationValidator configurationValidator;
    private final List<AiMealDraftProviderClient> providerClients;

    @Override
    public AiProviderSmokeResponseDto smoke(AiRequestType requestType) {
        AiRequestType safeType = requestType == null ? AiRequestType.VOICE_FOOD_LOG : requestType;
        AiProviderSmokeResponseDto response = baseResponse(safeType);
        try {
            configurationValidator.validateConfiguredForDraft();
            response.setConfigured(true);
            AiMealDraftProviderClient client = resolveClient();
            AiMealDraftResponseDto draft = safeType == AiRequestType.PHOTO_MEAL_LOG
                    ? client.createPhotoMealDraft(photoSmokeRequest())
                    : client.createVoiceFoodDraft(voiceSmokeRequest());
            response.setProviderReachable(true);
            response.setStatus("OK");
            response.setMessage("AI provider returned a smoke draft response.");
            response.setReturnedItemCount(draft.getItems() == null ? 0 : draft.getItems().size());
            return response;
        } catch (IllegalArgumentException ex) {
            response.setStatus("FAILED");
            response.setMessage(ex.getMessage());
            return response;
        }
    }

    private AiProviderSmokeResponseDto baseResponse(AiRequestType requestType) {
        AiProviderSmokeResponseDto response = new AiProviderSmokeResponseDto();
        response.setProvider(properties.getProvider());
        response.setModel(properties.getModel());
        response.setRequestType(requestType);
        response.setConfigured(false);
        response.setProviderReachable(false);
        response.setStatus("NOT_CHECKED");
        response.setCheckedAt(LocalDateTime.now());
        return response;
    }

    private AiMealDraftProviderClient resolveClient() {
        AiProvider provider = properties.getProvider();
        return providerClients.stream()
                .filter(client -> client.provider() == provider)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Configured AI provider client is not available."));
    }

    private AiVoiceFoodDraftRequestDto voiceSmokeRequest() {
        AiVoiceFoodDraftRequestDto request = new AiVoiceFoodDraftRequestDto();
        request.setTranscript("Smoke test only: 100 grams rice and 120 grams chicken for lunch.");
        request.setMealType("LUNCH");
        request.setLocale("en");
        request.setLogDate(LocalDateTime.now());
        return request;
    }

    private AiPhotoMealDraftRequestDto photoSmokeRequest() {
        AiPhotoMealDraftRequestDto request = new AiPhotoMealDraftRequestDto();
        request.setImageReference("https://example.com/grun-ai-smoke-meal.jpg");
        request.setUserNote("Smoke test only: simple lunch plate.");
        request.setMealType("LUNCH");
        request.setLogDate(LocalDateTime.now());
        return request;
    }
}
