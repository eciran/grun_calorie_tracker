package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiInsightRequestDto;
import com.grun.calorietracker.dto.AiInsightResponseDto;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.AiPhotoMealDraftRequestDto;
import com.grun.calorietracker.dto.AiRecipeDraftRequestDto;
import com.grun.calorietracker.dto.AiRecipeDraftResponseDto;
import com.grun.calorietracker.dto.AiVoiceFoodDraftRequestDto;
import com.grun.calorietracker.dto.AiWorkoutPlanDraftRequestDto;
import com.grun.calorietracker.dto.AiWorkoutPlanDraftResponseDto;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.exception.AiProviderException;
import com.grun.calorietracker.service.AiMealDraftProviderClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestOperations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OpenAiAiMealDraftProviderClient implements AiMealDraftProviderClient {

    private static final String SYSTEM_PROMPT = """
            You are GRun's bounded nutrition and fitness assistant. Return only valid JSON matching the requested schema.
            Do not give medical diagnosis, treatment advice, eating disorder advice, or unsafe exercise instructions.
            If confidence is low, set reviewRequired=true and add concise warnings.
            Use app-scoped estimates only; the user must confirm drafts before anything is logged.
            Numeric fields must be numbers only, never ranges or strings with units. Use null when unknown.
            Workout measurementType must be exactly one of DURATION, REPS, SETS_REPS, WEIGHT_REPS, DISTANCE, or MIXED.
            Food portionUnit must be exactly one of GRAM, MILLILITER, TABLESPOON, TEASPOON, SLICE, SERVING, or PIECE.
            """;

    private final AiProperties properties;
    private final RestOperations restOperations;
    private final ObjectMapper objectMapper;

    @Autowired
    public OpenAiAiMealDraftProviderClient(AiProperties properties, RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        this(properties, restTemplateBuilder
                .setConnectTimeout(properties.getOpenai().getTimeout())
                .setReadTimeout(properties.getOpenai().getTimeout())
                .build(), objectMapper);
    }

    public OpenAiAiMealDraftProviderClient(AiProperties properties, RestOperations restOperations, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restOperations = restOperations;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiProvider provider() {
        return AiProvider.OPENAI;
    }

    @Override
    public AiMealDraftResponseDto createVoiceFoodDraft(AiVoiceFoodDraftRequestDto request) {
        return callOpenAi(AiRequestType.VOICE_FOOD_LOG, "grun_meal_draft", mealDraftSchema(), List.of(textContent("Voice transcript meal logging request: " + writeJson(request))), AiMealDraftResponseDto.class);
    }

    @Override
    public AiMealDraftResponseDto createPhotoMealDraft(AiPhotoMealDraftRequestDto request) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(textContent("Photo meal logging request metadata: " + writeJson(request)));
        if (isOpenAiImageReference(request.getImageReference())) {
            content.add(imageContent(request.getImageReference()));
        } else {
            content.add(textContent("Image reference is not directly accessible by OpenAI. Return a cautious draft from metadata only and require review."));
        }
        return callOpenAi(AiRequestType.PHOTO_MEAL_LOG, "grun_meal_draft", mealDraftSchema(), content, AiMealDraftResponseDto.class);
    }

    @Override
    public AiRecipeDraftResponseDto createRecipeDraft(AiRecipeDraftRequestDto request) {
        return callOpenAi(AiRequestType.AI_RECIPE_GENERATION, "grun_recipe_draft", recipeDraftSchema(), List.of(textContent("Recipe generation request: " + writeJson(request))), AiRecipeDraftResponseDto.class);
    }

    @Override
    public AiWorkoutPlanDraftResponseDto createWorkoutPlanDraft(AiWorkoutPlanDraftRequestDto request) {
        return callOpenAi(AiRequestType.AI_WORKOUT_PLAN, "grun_workout_plan_draft", workoutPlanSchema(), List.of(textContent("Workout plan request: " + writeJson(request))), AiWorkoutPlanDraftResponseDto.class);
    }

    @Override
    public AiInsightResponseDto createDailyInsight(AiInsightRequestDto request) {
        return callOpenAi(AiRequestType.AI_DAILY_INSIGHT, "grun_insight", insightSchema(), List.of(textContent("Daily insight request: " + writeJson(request))), AiInsightResponseDto.class);
    }

    @Override
    public AiInsightResponseDto createWeeklyInsight(AiInsightRequestDto request) {
        return callOpenAi(AiRequestType.AI_WEEKLY_INSIGHT, "grun_insight", insightSchema(), List.of(textContent("Weekly insight request: " + writeJson(request))), AiInsightResponseDto.class);
    }

    private <T> T callOpenAi(AiRequestType requestType, String schemaName, Map<String, Object> schema, List<Map<String, Object>> userContent, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getOpenai().getApiKey());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("store", false);
        payload.put("input", List.of(
                message("system", List.of(textContent(SYSTEM_PROMPT + "\nRequest type: " + requestType))),
                message("user", userContent)
        ));
        payload.put("text", Map.of("format", Map.of(
                "type", "json_schema",
                "name", schemaName,
                "strict", false,
                "schema", schema
        )));

        String responseBody = null;
        String outputText = null;
        try {
            responseBody = restOperations.postForObject(
                    properties.getOpenai().getBaseUrl(),
                    new HttpEntity<>(payload, headers),
                    String.class
            );
            outputText = normalizeJsonOutput(extractOutputText(objectMapper.readTree(responseBody)));
            return objectMapper.copy()
                    .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
                    .readValue(outputText, responseType);
        } catch (RestClientResponseException ex) {
            String providerError = extractProviderError(ex.getResponseBodyAsString());
            log.warn("openai_provider_request_failed status={} error={}", ex.getStatusCode().value(), providerError);
            throw new AiProviderException("OpenAI provider request failed: HTTP "
                    + ex.getStatusCode().value() + " - " + providerError);
        } catch (RestClientException ex) {
            String providerError = sanitizeError(ex.getMessage());
            log.warn("openai_provider_request_failed transportError={}", providerError);
            throw new AiProviderException("OpenAI provider request failed: " + providerError);
        } catch (JsonProcessingException ex) {
            String error = sanitizeError(ex.getOriginalMessage());
            log.warn("openai_provider_invalid_json error={} outputSnippet={} responseSnippet={}",
                    error,
                    sanitizeSnippet(outputText),
                    sanitizeSnippet(responseBody));
            throw new AiProviderException("OpenAI provider returned an invalid JSON response: " + error);
        }
    }

    private String normalizeJsonOutput(String outputText) {
        if (outputText == null || outputText.isBlank()) {
            throw new AiProviderException("OpenAI provider returned no JSON output text.");
        }
        String trimmed = outputText.trim();
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            trimmed = trimmed.substring(3, trimmed.length() - 3).trim();
            if (trimmed.regionMatches(true, 0, "json", 0, 4)) {
                trimmed = trimmed.substring(4).trim();
            }
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }
        int objectStart = trimmed.indexOf('{');
        int objectEnd = trimmed.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1).trim();
        }
        int arrayStart = trimmed.indexOf('[');
        int arrayEnd = trimmed.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return trimmed.substring(arrayStart, arrayEnd + 1).trim();
        }
        return trimmed;
    }

    private String extractProviderError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "provider returned an empty error response";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = root.path("error");
            String message = textOrBlank(error.path("message"));
            String code = textOrBlank(error.path("code"));
            String type = textOrBlank(error.path("type"));
            StringBuilder builder = new StringBuilder();
            if (!type.isBlank()) {
                builder.append(type);
            }
            if (!code.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("/");
                }
                builder.append(code);
            }
            if (!message.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append(": ");
                }
                builder.append(message);
            }
            if (!builder.isEmpty()) {
                return sanitizeError(builder.toString());
            }
        } catch (JsonProcessingException ignored) {
            // Fall through to sanitized raw body.
        }
        return sanitizeError(responseBody);
    }

    private String textOrBlank(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : "";
    }

    private String sanitizeError(String value) {
        if (value == null || value.isBlank()) {
            return "provider request failed without details";
        }
        String sanitized = value.replaceAll("sk-[A-Za-z0-9_\\-]+", "sk-***").replaceAll("Bearer\\s+[^\\s,;]+", "Bearer ***").trim();
        return sanitized.length() > 700 ? sanitized.substring(0, 700) : sanitized;
    }


    private String sanitizeSnippet(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        String sanitized = sanitizeError(value).replaceAll("\\s+", " ");
        return sanitized.length() > 350 ? sanitized.substring(0, 350) : sanitized;
    }

    private String extractOutputText(JsonNode response) {
        if (response == null || response.isNull()) {
            throw new IllegalArgumentException("OpenAI provider returned an empty response.");
        }
        JsonNode outputText = response.get("output_text");
        if (outputText != null && outputText.isTextual() && !outputText.asText().isBlank()) {
            return outputText.asText();
        }
        JsonNode output = response.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.get("content");
                if (content == null || !content.isArray()) {
                    continue;
                }
                for (JsonNode part : content) {
                    JsonNode text = part.get("text");
                    if (text != null && text.isTextual() && !text.asText().isBlank()) {
                        return text.asText();
                    }
                }
            }
        }
        throw new IllegalArgumentException("OpenAI provider returned no JSON output text.");
    }

    private Map<String, Object> message(String role, List<Map<String, Object>> content) {
        return Map.of("role", role, "content", content);
    }

    private Map<String, Object> textContent(String text) {
        return Map.of("type", "input_text", "text", text);
    }

    private Map<String, Object> imageContent(String imageReference) {
        return Map.of("type", "input_image", "image_url", imageReference);
    }

    private boolean isOpenAiImageReference(String imageReference) {
        if (imageReference == null) {
            return false;
        }
        String normalized = imageReference.trim().toLowerCase();
        return normalized.startsWith("https://") || normalized.startsWith("data:image/");
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("AI provider input could not be serialized.");
        }
    }

    private Map<String, Object> mealDraftSchema() {
        return objectSchema(props(
                "suggestedMealType", stringSchema(),
                "summary", stringSchema(),
                "items", arraySchema(objectSchema(props(
                        "name", stringSchema(),
                        "quantity", numberSchema(),
                        "unit", stringSchema(),
                        "estimatedCalories", numberSchema(),
                        "estimatedProtein", numberSchema(),
                        "estimatedCarbs", numberSchema(),
                        "estimatedFat", numberSchema(),
                        "reviewRequired", booleanSchema(),
                        "matchReason", stringSchema(),
                        "safetyWarning", stringSchema(),
                        "confidence", numberSchema()
                )))
        ));
    }

    private Map<String, Object> recipeDraftSchema() {
        return objectSchema(props(
                "summary", stringSchema(),
                "reviewRequired", booleanSchema(),
                "suggestedRecipe", objectSchema(props(
                        "name", stringSchema(),
                        "description", stringSchema(),
                        "mealType", stringSchema(),
                        "language", stringSchema(),
                        "totalYieldGrams", numberSchema(),
                        "defaultServingGrams", numberSchema(),
                        "servingCount", integerSchema()
                )),
                "suggestedIngredients", arraySchema(objectSchema(props(
                        "name", stringSchema(),
                        "portionSize", numberSchema(),
                        "portionUnit", enumSchema("GRAM", "MILLILITER", "TABLESPOON", "TEASPOON", "SLICE", "SERVING", "PIECE"),
                        "reviewRequired", booleanSchema(),
                        "matchReason", stringSchema(),
                        "confidence", numberSchema()
                ))),
                "warnings", arraySchema(stringSchema())
        ));
    }

    private Map<String, Object> workoutPlanSchema() {
        return objectSchema(props(
                "name", stringSchema(),
                "summary", stringSchema(),
                "reviewRequired", booleanSchema(),
                "days", arraySchema(objectSchema(props(
                        "dayLabel", stringSchema(),
                        "focus", stringSchema(),
                        "exercises", arraySchema(objectSchema(props(
                                "name", stringSchema(),
                                "measurementType", enumSchema("DURATION", "REPS", "SETS_REPS", "WEIGHT_REPS", "DISTANCE", "MIXED"),
                                "setCount", integerSchema(),
                                "reps", integerSchema(),
                                "durationMinutes", integerSchema(),
                                "distanceKm", numberSchema(),
                                "weightKg", numberSchema(),
                                "rest", stringSchema(),
                                "rationale", stringSchema(),
                                "safetyNote", stringSchema()
                        )))
                ))),
                "warnings", arraySchema(stringSchema())
        ));
    }

    private Map<String, Object> insightSchema() {
        return objectSchema(props(
                "title", stringSchema(),
                "summary", stringSchema(),
                "highlights", arraySchema(stringSchema()),
                "warnings", arraySchema(stringSchema()),
                "recommendedActions", arraySchema(stringSchema())
        ));
    }
    private Map<String, Object> props(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put((String) values[i], values[i + 1]);
        }
        return map;
    }
    private Map<String, Object> objectSchema(Map<String, Object> properties) {
        return Map.of("type", "object", "properties", properties, "additionalProperties", true);
    }

    private Map<String, Object> arraySchema(Map<String, Object> items) {
        return Map.of("type", "array", "items", items);
    }

    private Map<String, Object> stringSchema() {
        return Map.of("type", List.of("string", "null"));
    }


    private Map<String, Object> enumSchema(String... values) {
        return Map.of("type", List.of("string", "null"), "enum", nullableEnumValues(values));
    }

    private List<Object> nullableEnumValues(String... values) {
        List<Object> enumValues = new ArrayList<>(List.of(values));
        enumValues.add(null);
        return enumValues;
    }

    private Map<String, Object> numberSchema() {
        return Map.of("type", List.of("number", "null"));
    }

    private Map<String, Object> integerSchema() {
        return Map.of("type", List.of("integer", "null"));
    }

    private Map<String, Object> booleanSchema() {
        return Map.of("type", List.of("boolean", "null"));
    }
}