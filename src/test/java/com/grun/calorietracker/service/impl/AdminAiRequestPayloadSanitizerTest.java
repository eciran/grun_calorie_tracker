package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminAiRequestPayloadSanitizerTest {

    private final AdminAiRequestPayloadSanitizer sanitizer = new AdminAiRequestPayloadSanitizer(new ObjectMapper());

    @Test
    void sanitizeInput_returnsOnlyExplicitlyApprovedSummaryFields() {
        JsonNode result = sanitizer.sanitizeInput("""
                {
                  "dayCount": 7,
                  "mealsPerDay": 6,
                  "language": "en",
                  "prompt": "private user prompt",
                  "transcript": "private voice content",
                  "imageReference": "https://signed.example/image",
                  "unknownInternalField": "must not leave backend"
                }
                """);

        assertEquals(7, result.get("dayCount").asInt());
        assertEquals(6, result.get("mealsPerDay").asInt());
        assertEquals("en", result.get("language").asText());
        assertFalse(result.has("prompt"));
        assertFalse(result.has("transcript"));
        assertFalse(result.has("imageReference"));
        assertFalse(result.has("unknownInternalField"));
    }

    @Test
    void sanitizeOutput_filtersSensitiveAndUnknownNestedFieldsAndCapsArrays() {
        String items = IntStream.range(0, 40)
                .mapToObj(index -> "{\"name\":\"Meal " + index + "\",\"calories\":100,\"secret\":\"hidden\",\"internal\":\"hidden\"}")
                .reduce((left, right) -> left + "," + right)
                .orElse("");

        JsonNode result = sanitizer.sanitizeOutput("{\"summary\":\"Useful result\",\"items\":[" + items + "],\"rawProviderPayload\":\"hidden\"}", com.grun.calorietracker.enums.AiRequestType.PHOTO_MEAL_LOG);

        assertEquals("Useful result", result.get("summary").asText());
        assertEquals(30, result.get("items").size());
        assertEquals("Meal 0", result.get("items").get(0).get("name").asText());
        assertFalse(result.get("items").get(0).has("secret"));
        assertFalse(result.get("items").get(0).has("internal"));
        assertFalse(result.has("rawProviderPayload"));
    }

    @Test
    void sanitizeRecipeOutput_keepsOnlySummaryIngredientsRemainingQuotaAndPerServingNutrition() {
        JsonNode result = sanitizer.sanitizeOutput("""
                {
                  "summary":"Recipe ready",
                  "aiRemainingThisPeriod":42,
                  "suggestedIngredients":[{"name":"Chicken","portionSize":150,"portionUnit":"GRAM"}],
                  "estimatedNutritionTotal":{"calories":900},
                  "estimatedNutritionPerServing":{"calories":450,"protein":38},
                  "nutritionEstimateNote":"hidden note",
                  "cookingTips":["hidden tip"],
                  "substitutions":["hidden substitution"],
                  "warnings":["hidden warning"]
                }
                """, com.grun.calorietracker.enums.AiRequestType.AI_RECIPE_GENERATION);

        assertEquals("Recipe ready", result.get("summary").asText());
        assertEquals(42, result.get("aiRemainingThisPeriod").asInt());
        assertEquals("Chicken", result.get("suggestedIngredients").get(0).get("name").asText());
        assertEquals(450, result.get("estimatedNutritionPerServing").get("calories").asInt());
        assertFalse(result.has("estimatedNutritionTotal"));
        assertFalse(result.has("nutritionEstimateNote"));
        assertFalse(result.has("cookingTips"));
        assertFalse(result.has("substitutions"));
        assertFalse(result.has("warnings"));
    }
    @Test
    void sanitizeText_redactsUrlsAndCredentialLikeValues() {
        String result = sanitizer.sanitizeText("Download https://signed.example/file?token=abc apiKey=secret-value Bearer abc.def");

        assertTrue(result.contains("[URL REDACTED]"));
        assertFalse(result.contains("signed.example"));
        assertFalse(result.contains("secret-value"));
        assertFalse(result.contains("abc.def"));
    }
}