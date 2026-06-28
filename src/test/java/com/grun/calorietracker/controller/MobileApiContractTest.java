package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MobileApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void openApi_containsStableMobileV1PathsOnly() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode paths = objectMapper.readTree(body).path("paths");

        List<String> requiredMobilePaths = List.of(
                "/api/v1/auth/register",
                "/api/v1/auth/login",
                "/api/v1/auth/refresh",
                "/api/v1/auth/logout",
                "/api/v1/auth/email-verification/resend",
                "/api/v1/auth/email-verification/confirm",
                "/api/v1/auth/password-reset/request",
                "/api/v1/auth/password-reset/confirm",
                "/api/v1/app/startup",
                "/api/v1/onboarding/complete",
                "/api/v1/users/me",
                "/api/v1/analytics/events",
                "/api/v1/quick-log/suggestions",
                "/api/v1/products/search",
                "/api/v1/products/barcode/{barcode}",
                "/api/v1/food-logs",
                "/api/v1/food-logs/stats",
                "/api/v1/water-logs",
                "/api/v1/water-logs/daily-summary",
                "/api/v1/water-logs/goal",
                "/api/v1/water-logs/reminder-settings",
                "/api/v1/steps/goal",
                "/api/v1/steps/daily-summary",
                "/api/v1/steps/range-summary",
                "/api/v1/steps/manual-logs",
                "/api/v1/fasting/plan",
                "/api/v1/fasting/sessions/start",
                "/api/v1/fasting/sessions/{id}/finish",
                "/api/v1/fasting/sessions/{id}/cancel",
                "/api/v1/fasting/summary",
                "/api/v1/fasting/summary/range",
                "/api/v1/exercise-logs/history",
                "/api/v1/progress",
                "/api/v1/subscriptions/me",
                "/api/v1/subscriptions/me/features",
                "/api/v1/ai/meal-drafts/voice",
                "/api/v1/ai/meal-drafts/photo",
                "/api/v1/ai/meal-drafts/{requestId}/confirm",
                "/api/v1/ai/meal-drafts/{requestId}/reject",
                "/api/v1/ai/meal-drafts/history",
                "/api/v1/ai/recipes/generate",
                "/api/v1/ai/recipes/{requestId}/confirm",
                "/api/v1/recipes",
                "/api/v1/recipes/{id}",
                "/api/v1/recipes/{id}/image",
                "/api/v1/recipes/images/{filename}",
                "/api/v1/recipes/public",
                "/api/v1/recipes/public/categories",
                "/api/v1/recipes/public/{id}",
                "/api/v1/recipes/public/{id}/copy",
                "/api/v1/recipes/public/{id}/report",
                "/api/v1/recipes/{id}/publish-request",
                "/api/v1/recipes/saved",
                "/api/v1/recipes/favorites",
                "/api/v1/recipes/{id}/interaction",
                "/api/v1/recipes/{id}/logs",
                "/api/v1/recipes/logs",
                "/api/v1/recipes/logs/{logId}",
                "/api/v1/meal-plans",
                "/api/v1/meal-plans/{planId}",
                "/api/v1/meal-plans/{planId}/duplicate",
                "/api/v1/meal-plans/{planId}/grocery-list",
                "/api/v1/notifications",
                "/api/v1/devices/push-tokens",
                "/api/v1/devices/push-tokens/{id}",
                "/api/v1/account/legal/consents",
                "/api/v1/account/gdpr/export",
                "/api/v1/health/connections"
        );

        requiredMobilePaths.forEach(path ->
                assertTrue(paths.has(path), () -> "Missing mobile API contract path: " + path));

        List<String> removedOrNonVersionedPaths = List.of(
                "/api/auth/register",
                "/api/auth/login",
                "/api/users",
                "/api/products/search",
                "/api/food-logs",
                "/saveLogs",
                "/getLogs",
                "/api/v1/subscriptions/me/ai-quota/consume"
        );

        removedOrNonVersionedPaths.forEach(path ->
                assertFalse(paths.has(path), () -> "Non-versioned or removed path should not be exposed: " + path));

        JsonNode publicRecipeParameters = paths.path("/api/v1/recipes/public").path("get").path("parameters");
        assertTrue(hasParameter(publicRecipeParameters, "excludeAllergens"), "Public recipe discovery should expose excludeAllergens filter.");
    }

    private boolean hasParameter(JsonNode parameters, String name) {
        for (JsonNode parameter : parameters) {
            if (name.equals(parameter.path("name").asText())) {
                return true;
            }
        }
        return false;
    }
}
