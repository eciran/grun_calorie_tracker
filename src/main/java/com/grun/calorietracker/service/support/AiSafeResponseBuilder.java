package com.grun.calorietracker.service.support;

import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AiSafeResponseBuilder {

    public static final String GENERIC_AI_FAILURE_MESSAGE =
            "AI analysis could not be completed. Please try again with a different input.";

    private static final String SCHEMA_VERSION = "ai_error_v1";
    private static final String ERROR_CODE = "AI_ANALYSIS_FAILED";
    private static final String USER_ACTION =
            "Try again with a clearer photo, shorter voice input, or more specific details.";

    private AiSafeResponseBuilder() {
    }

    public static Map<String, Object> failurePayload(AiRequestType requestType, boolean retryable) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", SCHEMA_VERSION);
        payload.put("requestType", requestType);
        payload.put("status", AiRequestStatus.FAILED);
        payload.put("errorCode", ERROR_CODE);
        payload.put("userMessage", GENERIC_AI_FAILURE_MESSAGE);
        payload.put("userAction", USER_ACTION);
        payload.put("retryable", retryable);
        payload.put("nextBestActions", nextBestActions(requestType));
        payload.put("createdAt", LocalDateTime.now());
        return payload;
    }

    private static List<String> nextBestActions(AiRequestType requestType) {
        if (requestType == AiRequestType.PHOTO_MEAL_LOG) {
            return List.of(
                    "Use a well-lit photo where the whole plate is visible.",
                    "Add a short note with portion size or key ingredients.",
                    "Retake the photo if the meal is covered or blurry."
            );
        }
        if (requestType == AiRequestType.VOICE_FOOD_LOG) {
            return List.of(
                    "Describe the food, portion size, and cooking method.",
                    "Keep the voice input short and specific.",
                    "Use manual entry if the meal has many unknown ingredients."
            );
        }
        if (requestType == AiRequestType.AI_RECIPE_GENERATION) {
            return List.of(
                    "Provide target calories, meal type, and preferred ingredients.",
                    "Mention allergies or foods to avoid.",
                    "Try again with a narrower recipe goal."
            );
        }
        if (requestType == AiRequestType.AI_WORKOUT_PLAN) {
            return List.of(
                    "Provide goal, fitness level, available days, and equipment.",
                    "Mention injuries or movement limitations.",
                    "Try again with a shorter plan duration."
            );
        }
        return List.of(
                "Try again with clearer context.",
                "Review your input before sending another AI request."
        );
    }
}
