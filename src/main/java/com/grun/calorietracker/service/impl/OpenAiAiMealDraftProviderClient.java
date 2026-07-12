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
import com.grun.calorietracker.dto.AiProductQualityValidationRequestDto;
import com.grun.calorietracker.dto.AiProductQualityValidationResponseDto;
import com.grun.calorietracker.dto.AiRecipeDraftRequestDto;
import com.grun.calorietracker.dto.AiRecipeDraftResponseDto;
import com.grun.calorietracker.dto.AiUsageMetadataCarrier;
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
            Prefer precise, user-actionable outputs over generic advice. Every user-visible sentence must feel professional, specific, and worth paying for in a Pro plan. Avoid filler, generic motivation, vague wellness language, and unsupported certainty. For insights, explain what data was analyzed, what signals are missing, why each finding matters, and what the user should do next. Include reviewReasons when confidence is low or data is uncertain.
            qualityScore must be an integer from 0 to 100. confidence must be a number from 0 to 1. estimatedUncertainty must be LOW, MEDIUM, or HIGH.
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
        return callOpenAi(AiRequestType.VOICE_FOOD_LOG, "grun_meal_draft", mealDraftSchema(), List.of(textContent("Create a premium editable meal snapshot from this voice transcript. Include a polished userMessage, professionalSummary, assumptions, nextBestActions, and item-level reasoning/portion notes. Voice transcript meal logging request: " + writeJson(request))), AiMealDraftResponseDto.class);
    }

    @Override
    public AiMealDraftResponseDto createPhotoMealDraft(AiPhotoMealDraftRequestDto request) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(textContent("Create a premium editable meal snapshot from this photo request. Include a polished userMessage, professionalSummary, assumptions, nextBestActions, and item-level reasoning/portion notes. Photo meal logging request metadata: " + writeJson(request)));
        if (isOpenAiImageReference(request.getImageReference())) {
            content.add(imageContent(request.getImageReference()));
        } else {
            content.add(textContent("Image reference is not directly accessible by OpenAI. Return a cautious draft from metadata only and require review."));
        }
        return callOpenAi(AiRequestType.PHOTO_MEAL_LOG, "grun_meal_draft", mealDraftSchema(), content, AiMealDraftResponseDto.class);
    }

    @Override
    public AiRecipeDraftResponseDto createRecipeDraft(AiRecipeDraftRequestDto request) {
        return callOpenAi(AiRequestType.AI_RECIPE_GENERATION, "grun_recipe_draft", recipeDraftSchema(), List.of(textContent("Create a premium, user-ready recipe draft. Include a polished userMessage, professionalSummary, assumptions, nextBestActions, cooking tips, substitutions, cooking steps, prep/cook timing guidance, macro and micronutrient estimates for total recipe and per serving, and clear nutrition uncertainty notes. Recipe generation request: " + writeJson(request))), AiRecipeDraftResponseDto.class);
    }

    @Override
    public AiWorkoutPlanDraftResponseDto createWorkoutPlanDraft(AiWorkoutPlanDraftRequestDto request) {
        return callOpenAi(AiRequestType.AI_WORKOUT_PLAN, "grun_workout_plan_draft", workoutPlanSchema(), List.of(textContent("Create a premium, safe, user-ready workout plan draft. Include a polished userMessage, professionalSummary, assumptions, nextBestActions, training principles, exact sets, reps or duration, rest, warm-up, cool-down, execution instructions, form cues, common mistakes, tempo, progression, coaching notes, alternatives, rationale, and safety notes for every exercise. For DURATION exercises, durationMinutes is required. For SETS_REPS or WEIGHT_REPS exercises, setCount and reps are required. For REPS exercises, reps is required. For DISTANCE exercises, distanceKm is required. Use provided exercise catalog ids when confident; set exerciseItemId to 0 when there is no confident catalog match. Workout plan request: " + writeJson(request))), AiWorkoutPlanDraftResponseDto.class);
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
        if (properties.getOpenai().getMaxOutputTokens() > 0) {
            payload.put("max_output_tokens", properties.getOpenai().getMaxOutputTokens());
        }
        payload.put("text", Map.of("format", Map.of(
                "type", "json_schema",
                "name", schemaName,
                "strict", true,
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
            JsonNode root = objectMapper.readTree(responseBody);
            outputText = normalizeJsonOutput(extractOutputText(root));
            T result = objectMapper.copy()
                    .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
                    .readValue(outputText, responseType);
            attachUsageMetadata(result, root.path("usage"));
            return result;
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

    private void attachUsageMetadata(Object result, JsonNode usage) {
        if (!(result instanceof AiUsageMetadataCarrier carrier) || usage == null || usage.isMissingNode() || usage.isNull()) {
            return;
        }
        Integer inputTokens = integerOrNull(usage.path("input_tokens"));
        Integer outputTokens = integerOrNull(usage.path("output_tokens"));
        Integer totalTokens = integerOrNull(usage.path("total_tokens"));
        carrier.setPromptTokens(inputTokens);
        carrier.setCompletionTokens(outputTokens);
        carrier.setTotalTokens(totalTokens);
        carrier.setEstimatedCost(estimateCost(inputTokens, outputTokens));
        carrier.setCostCurrency(properties.getOpenai().getCostCurrency());
    }

    private Integer integerOrNull(JsonNode node) {
        return node != null && node.isNumber() ? node.asInt() : null;
    }

    private Double estimateCost(Integer inputTokens, Integer outputTokens) {
        double inputCost = properties.getOpenai().getInputTokenCostPer1m();
        double outputCost = properties.getOpenai().getOutputTokenCostPer1m();
        if ((inputTokens == null && outputTokens == null) || (inputCost <= 0 && outputCost <= 0)) {
            return null;
        }
        double total = (inputTokens == null ? 0 : inputTokens) * inputCost
                + (outputTokens == null ? 0 : outputTokens) * outputCost;
        return total / 1_000_000d;
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
        String status = textOrBlank(response.path("status"));
        if ("incomplete".equalsIgnoreCase(status)) {
            String reason = textOrBlank(response.path("incomplete_details").path("reason"));
            throw new AiProviderException("OpenAI provider returned an incomplete response"
                    + (reason.isBlank() ? "." : ": " + sanitizeError(reason)));
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
                "schemaVersion", enumSchema("ai_response_v3"),
                "suggestedMealType", stringSchema(),
                "summary", stringSchema(),
                "resultType", enumSchema("AI_SNAPSHOT"),
                "userMessage", stringSchema(),
                "professionalSummary", stringSchema(),
                "assumptions", arraySchema(stringSchema()),
                "nextBestActions", arraySchema(stringSchema()),
                "confidence", numberSchema(),
                "qualityScore", integerSchema(),
                "estimatedUncertainty", enumSchema("LOW", "MEDIUM", "HIGH"),
                "reviewReasons", arraySchema(stringSchema()),
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
                        "confidence", numberSchema(),
                        "portionEstimateMethod", enumSchema("USER_DECLARED", "VISUAL_ESTIMATE", "TEXT_INFERRED", "UNKNOWN"),
                        "reasoning", stringSchema(),
                        "portionNote", stringSchema(),
                        "visibleInPhoto", booleanSchema(),
                        "needsUserPortionConfirmation", booleanSchema(),
                        "alternativeMatchNames", arraySchema(stringSchema())
                )))
        ));
    }
    private Map<String, Object> recipeDraftSchema() {
        return objectSchema(props(
                "schemaVersion", enumSchema("ai_response_v3"),
                "summary", stringSchema(),
                "resultType", enumSchema("AI_SNAPSHOT"),
                "userMessage", stringSchema(),
                "professionalSummary", stringSchema(),
                "assumptions", arraySchema(stringSchema()),
                "nextBestActions", arraySchema(stringSchema()),
                "reviewRequired", booleanSchema(),
                "confidence", numberSchema(),
                "qualityScore", integerSchema(),
                "estimatedUncertainty", enumSchema("LOW", "MEDIUM", "HIGH"),
                "reviewReasons", arraySchema(stringSchema()),
                "suggestedRecipe", objectSchema(props(
                        "name", stringSchema(),
                        "description", stringSchema(),
                        "mealType", stringSchema(),
                        "language", stringSchema(),
                        "totalYieldGrams", numberSchema(),
                        "defaultServingGrams", numberSchema(),
                        "servingCount", integerSchema(),
                        "cookingSteps", arraySchema(objectSchema(props(
                                "instruction", stringSchema()
                        )))
                )),
                "estimatedNutritionTotal", nutritionSchema(),
                "estimatedNutritionPerServing", nutritionSchema(),
                "nutritionEstimateNote", stringSchema(),
                "suggestedIngredients", arraySchema(objectSchema(props(
                        "name", stringSchema(),
                        "portionSize", numberSchema(),
                        "portionUnit", enumSchema("GRAM", "MILLILITER", "TABLESPOON", "TEASPOON", "SLICE", "SERVING", "PIECE"),
                        "reviewRequired", booleanSchema(),
                        "matchReason", stringSchema(),
                        "confidence", numberSchema(),
                        "preparationNote", stringSchema(),
                        "nutritionNote", stringSchema()
                ))),
                "cookingTips", arraySchema(stringSchema()),
                "substitutions", arraySchema(stringSchema()),
                "warnings", arraySchema(stringSchema())
        ));
    }
    private Map<String, Object> nutritionSchema() {
        return objectSchema(props(
                "calories", numberSchema(),
                "protein", numberSchema(),
                "carbs", numberSchema(),
                "fat", numberSchema(),
                "fiber", numberSchema(),
                "sugar", numberSchema(),
                "saturatedFat", numberSchema(),
                "sodium", numberSchema(),
                "potassium", numberSchema(),
                "cholesterol", numberSchema(),
                "calcium", numberSchema(),
                "iron", numberSchema(),
                "magnesium", numberSchema(),
                "zinc", numberSchema(),
                "vitaminA", numberSchema(),
                "vitaminC", numberSchema(),
                "vitaminD", numberSchema(),
                "vitaminE", numberSchema(),
                "vitaminB12", numberSchema()
        ));
    }

    private Map<String, Object> workoutPlanSchema() {
        return objectSchema(props(
                "schemaVersion", enumSchema("ai_response_v3"),
                "name", stringSchema(),
                "summary", stringSchema(),
                "resultType", enumSchema("AI_WORKOUT_PLAN_DRAFT"),
                "userMessage", stringSchema(),
                "professionalSummary", stringSchema(),
                "assumptions", arraySchema(stringSchema()),
                "nextBestActions", arraySchema(stringSchema()),
                "reviewRequired", booleanSchema(),
                "confidence", numberSchema(),
                "qualityScore", integerSchema(),
                "estimatedUncertainty", enumSchema("LOW", "MEDIUM", "HIGH"),
                "reviewReasons", arraySchema(stringSchema()),
                "days", arraySchema(objectSchema(props(
                        "dayLabel", stringSchema(),
                        "focus", stringSchema(),
                        "estimatedDurationMinutes", integerSchema(),
                        "warmup", stringSchema(),
                        "cooldown", stringSchema(),
                        "exercises", arraySchema(objectSchema(props(
                                "exerciseItemId", integerSchema(),
                                "name", stringSchema(),
                                "measurementType", enumSchema("DURATION", "REPS", "SETS_REPS", "WEIGHT_REPS", "DISTANCE", "MIXED"),
                                "setCount", integerSchema(),
                                "reps", integerSchema(),
                                "durationMinutes", integerSchema(),
                                "distanceKm", numberSchema(),
                                "weightKg", numberSchema(),
                                "rest", stringSchema(),
                                "restSeconds", integerSchema(),
                                "intensity", enumSchema("LOW", "MODERATE", "HIGH"),
                                "rationale", stringSchema(),
                                "executionInstructions", stringSchema(),
                                "formCues", arraySchema(stringSchema()),
                                "commonMistakes", arraySchema(stringSchema()),
                                "tempo", stringSchema(),
                                "alternatives", arraySchema(stringSchema()),
                                "progressionNote", stringSchema(),
                                "coachingNote", stringSchema(),
                                "targetMuscleGroup", stringSchema(),
                                "equipmentUsed", stringSchema(),
                                "safetyNote", stringSchema(),
                                "reviewRequired", booleanSchema()
                        )))
                ))),
                "trainingPrinciples", arraySchema(stringSchema()),
                "warnings", arraySchema(stringSchema())
        ));
    }

    @Override
    public AiProductQualityValidationResponseDto validateProductQuality(AiProductQualityValidationRequestDto request) {
        return callOpenAi(AiRequestType.AI_RECIPE_GENERATION, "grun_product_quality_validation", productQualityValidationSchema(), List.of(textContent("Validate this food product data for admin review. Identify missing or suspicious macro/micro nutrition, serving-size, source, region, image/label, or macro-calorie consistency issues. Do not invent exact nutrition values unless strongly inferable from the supplied data; prefer null suggestedValue with review-required reason when source evidence is insufficient. Product data: " + writeJson(request))), AiProductQualityValidationResponseDto.class);
    }


    private Map<String, Object> productQualityValidationSchema() {
        return objectSchema(props(
                "summary", stringSchema(),
                "confidence", numberSchema(),
                "qualityScore", integerSchema(),
                "reviewRequired", booleanSchema(),
                "issues", arraySchema(objectSchema(props(
                        "suggestionType", enumSchema(
                                "MISSING_MACRO_DATA",
                                "MISSING_MICRO_DATA",
                                "SUSPICIOUS_CALORIE_VALUE",
                                "MACRO_CALORIE_MISMATCH",
                                "SUSPICIOUS_SODIUM_VALUE",
                                "MISSING_SERVING_SIZE",
                                "SOURCE_CONFLICT",
                                "IMAGE_REVIEW_REQUIRED",
                                "REGION_MISMATCH",
                                "LABEL_REVIEW_REQUIRED"
                        ),
                        "fieldName", stringSchema(),
                        "currentValue", stringSchema(),
                        "suggestedValue", stringSchema(),
                        "reason", stringSchema(),
                        "confidenceScore", integerSchema()
                )))
        ));
    }
    private Map<String, Object> insightSchema() {
        return objectSchema(props(
                "schemaVersion", enumSchema("ai_response_v3"),
                "title", stringSchema(),
                "summary", stringSchema(),
                "highlights", arraySchema(stringSchema()),
                "warnings", arraySchema(stringSchema()),
                "recommendedActions", arraySchema(stringSchema()),
                "confidence", numberSchema(),
                "qualityScore", integerSchema(),
                "priority", enumSchema("LOW", "MEDIUM", "HIGH"),
                "category", enumSchema("CALORIES", "PROTEIN", "CARBS", "FAT", "HYDRATION", "EXERCISE", "CONSISTENCY", "RECOVERY", "GENERAL"),
                "actionType", enumSchema("LOG_FOOD", "ADD_PROTEIN", "DRINK_WATER", "PLAN_WORKOUT", "REVIEW_GOAL", "KEEP_STREAK", "REST", "NONE"),
                "linkedMetric", stringSchema(),
                "ctaLabel", stringSchema(),
                "ctaTarget", stringSchema(),
                "reviewReasons", arraySchema(stringSchema()),
                "dataCoverage", objectSchema(props(
                        "daysAnalyzed", integerSchema(),
                        "mealsLogged", integerSchema(),
                        "exerciseLogged", booleanSchema(),
                        "exerciseMinutes", integerSchema(),
                        "diaryDays", integerSchema(),
                        "signalsUsed", arraySchema(stringSchema()),
                        "missingSignals", arraySchema(stringSchema()),
                        "confidenceLabel", enumSchema("LOW", "MEDIUM", "HIGH")
                )),
                "keyFindings", arraySchema(objectSchema(props(
                        "type", enumSchema("trend", "pattern", "risk", "quality", "consistency", "opportunity"),
                        "label", stringSchema(),
                        "message", stringSchema(),
                        "evidence", stringSchema(),
                        "impact", stringSchema(),
                        "severity", enumSchema("LOW", "MEDIUM", "HIGH")
                ))),
                "personalizedActions", arraySchema(objectSchema(props(
                        "priority", integerSchema(),
                        "action", stringSchema(),
                        "reason", stringSchema(),
                        "expectedImpact", stringSchema(),
                        "effort", enumSchema("LOW", "MEDIUM", "HIGH"),
                        "linkedMetric", stringSchema()
                ))),
                "tomorrowFocus", stringSchema(),
                "watchOut", stringSchema(),
                "dataQualityNote", stringSchema()
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
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", new ArrayList<>(properties.keySet()),
                "additionalProperties", false
        );
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



