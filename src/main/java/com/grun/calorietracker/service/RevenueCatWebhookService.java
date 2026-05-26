package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.grun.calorietracker.dto.RevenueCatWebhookResponseDto;

public interface RevenueCatWebhookService {
    RevenueCatWebhookResponseDto processWebhook(String authorizationHeader, JsonNode payload);
    RevenueCatWebhookResponseDto retryStoredEvent(Long eventId);
}
