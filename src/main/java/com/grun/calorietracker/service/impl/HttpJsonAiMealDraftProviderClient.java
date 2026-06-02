package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.AiPhotoMealDraftRequestDto;
import com.grun.calorietracker.dto.AiVoiceFoodDraftRequestDto;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.service.AiMealDraftProviderClient;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

@Component
public class HttpJsonAiMealDraftProviderClient implements AiMealDraftProviderClient {

    private final AiProperties properties;
    private final RestOperations restOperations;

    @Autowired
    public HttpJsonAiMealDraftProviderClient(AiProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this(properties, restTemplateBuilder
                .setConnectTimeout(properties.getHttpJson().getTimeout())
                .setReadTimeout(properties.getHttpJson().getTimeout())
                .build());
    }

    public HttpJsonAiMealDraftProviderClient(AiProperties properties, RestOperations restOperations) {
        this.properties = properties;
        this.restOperations = restOperations;
    }

    @Override
    public AiProvider provider() {
        return AiProvider.HTTP_JSON;
    }

    @Override
    public AiMealDraftResponseDto createVoiceFoodDraft(AiVoiceFoodDraftRequestDto request) {
        return callProvider(new ProviderRequest(AiRequestType.VOICE_FOOD_LOG, properties.getModel(), request));
    }

    @Override
    public AiMealDraftResponseDto createPhotoMealDraft(AiPhotoMealDraftRequestDto request) {
        return callProvider(new ProviderRequest(AiRequestType.PHOTO_MEAL_LOG, properties.getModel(), request));
    }

    private AiMealDraftResponseDto callProvider(ProviderRequest payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getHttpJson().getApiKey());
        headers.set("X-GRun-AI-Provider", "HTTP_JSON");
        headers.set("X-GRun-AI-Model", properties.getModel());

        try {
            AiMealDraftResponseDto response = restOperations.postForObject(
                    properties.getHttpJson().getEndpoint(),
                    new HttpEntity<>(payload, headers),
                    AiMealDraftResponseDto.class
            );
            if (response == null) {
                throw new IllegalArgumentException("HTTP JSON AI provider returned an empty response.");
            }
            return response;
        } catch (RestClientException ex) {
            throw new IllegalArgumentException("HTTP JSON AI provider request failed.");
        }
    }

    private record ProviderRequest(AiRequestType requestType, String model, Object input) {
    }
}
