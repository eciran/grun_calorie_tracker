package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiInsightRequestDto;
import com.grun.calorietracker.dto.AiInsightResponseDto;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.AiNutritionPlanDraftRequestDto;
import com.grun.calorietracker.dto.AiNutritionPlanDraftResponseDto;
import com.grun.calorietracker.dto.AiPhotoMealDraftRequestDto;
import com.grun.calorietracker.dto.AiPreparationGuideProviderRequestDto;
import com.grun.calorietracker.dto.AiPreparationGuideResponseDto;
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
import com.grun.calorietracker.exception.AiProviderTimeoutException;
import com.grun.calorietracker.service.AiMealDraftProviderClient;
import com.grun.calorietracker.service.prompt.AiPromptTemplates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestOperations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gemini test adapter. The application-facing DTOs, prompts and strict response
 * schemas intentionally remain identical to the OpenAI adapter.
 */
@Slf4j
@Component
public class GeminiAiMealDraftProviderClient implements AiMealDraftProviderClient {

    private final AiProperties properties;
    private final RestOperations restOperations;
    private final ObjectMapper objectMapper;
    private final OpenAiAiMealDraftProviderClient contract;

    @Autowired
    public GeminiAiMealDraftProviderClient(AiProperties properties,
                                           RestTemplateBuilder restTemplateBuilder,
                                           ObjectMapper objectMapper,
                                           OpenAiAiMealDraftProviderClient contract) {
        this(properties, restTemplateBuilder
                .setConnectTimeout(properties.getGemini().getConnectTimeout())
                .setReadTimeout(properties.getGemini().getTimeout())
                .build(), objectMapper, contract);
    }

    public GeminiAiMealDraftProviderClient(AiProperties properties,
                                           RestOperations restOperations,
                                           ObjectMapper objectMapper,
                                           OpenAiAiMealDraftProviderClient contract) {
        this.properties = properties;
        this.restOperations = restOperations;
        this.objectMapper = objectMapper;
        this.contract = contract;
    }

    @Override
    public AiProvider provider() {
        return AiProvider.GEMINI;
    }

    @Override
    public AiMealDraftResponseDto createVoiceFoodDraft(AiVoiceFoodDraftRequestDto request) {
        return callGemini(AiRequestType.VOICE_FOOD_LOG, contract.mealDraftSchema(false),
                List.of(textPart(AiPromptTemplates.request(AiRequestType.VOICE_FOOD_LOG, contract.writeJson(request)))),
                AiMealDraftResponseDto.class, maxOutputTokens());
    }

    @Override
    public AiMealDraftResponseDto createPhotoMealDraft(AiPhotoMealDraftRequestDto request) {
        boolean alternatives = properties.getPhoto().isAlternativeSnapshotsEnabled();
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(textPart(AiPromptTemplates.photo(contract.writeJson(request), alternatives,
                properties.getPhoto().getMaxAlternativeSnapshots())));
        parts.add(textPart(AiPromptTemplates.PHOTO_PORTION_RULES));
        String reference = contract.resolveManagedImageReference(request.getImageReference());
        Map<String, Object> image = inlineImagePart(reference);
        if (image == null) {
            parts.add(textPart("Image reference is not directly accessible by Gemini. Return a cautious draft from metadata only and require review."));
        } else {
            parts.add(image);
        }
        return callGemini(AiRequestType.PHOTO_MEAL_LOG, contract.photoMealDraftSchema(alternatives), parts,
                AiMealDraftResponseDto.class, maxOutputTokens());
    }

    @Override
    public AiRecipeDraftResponseDto createRecipeDraft(AiRecipeDraftRequestDto request) {
        return callGemini(AiRequestType.AI_RECIPE_GENERATION, contract.recipeDraftSchema(),
                promptParts(AiPromptTemplates.request(AiRequestType.AI_RECIPE_GENERATION, contract.writeJson(request))),
                AiRecipeDraftResponseDto.class, maxOutputTokens());
    }

    @Override
    public AiPreparationGuideResponseDto createPreparationGuide(AiPreparationGuideProviderRequestDto request) {
        return callGemini(AiRequestType.AI_MEAL_PREPARATION_GUIDE, contract.preparationGuideSchema(),
                promptParts(AiPromptTemplates.request(AiRequestType.AI_MEAL_PREPARATION_GUIDE, contract.writeJson(request))),
                AiPreparationGuideResponseDto.class, maxOutputTokens());
    }

    @Override
    public AiNutritionPlanDraftResponseDto createNutritionPlanDraft(AiNutritionPlanDraftRequestDto request) {
        String prompt = AiPromptTemplates.nutrition(contract.nutritionPlanTargetGuardrails(request),
                contract.writeJson(request));
        return callGemini(AiRequestType.AI_NUTRITION_PLAN, contract.nutritionPlanSchema(), promptParts(prompt),
                AiNutritionPlanDraftResponseDto.class,
                geminiNutritionPlanOutputTokenBudget());
    }

    @Override
    public AiWorkoutPlanDraftResponseDto createWorkoutPlanDraft(AiWorkoutPlanDraftRequestDto request) {
        return callGemini(AiRequestType.AI_WORKOUT_PLAN, contract.workoutPlanSchema(),
                promptParts(AiPromptTemplates.request(AiRequestType.AI_WORKOUT_PLAN, contract.writeJson(request))),
                AiWorkoutPlanDraftResponseDto.class, maxOutputTokens());
    }

    @Override
    public AiInsightResponseDto createDailyInsight(AiInsightRequestDto request) {
        return callGemini(AiRequestType.AI_DAILY_INSIGHT, contract.insightSchema(),
                promptParts(AiPromptTemplates.request(AiRequestType.AI_DAILY_INSIGHT, contract.writeJson(request))),
                AiInsightResponseDto.class, maxOutputTokens());
    }

    @Override
    public AiInsightResponseDto createWeeklyInsight(AiInsightRequestDto request) {
        return callGemini(AiRequestType.AI_WEEKLY_INSIGHT, contract.insightSchema(),
                promptParts(AiPromptTemplates.request(AiRequestType.AI_WEEKLY_INSIGHT, contract.writeJson(request))),
                AiInsightResponseDto.class, maxOutputTokens());
    }

    @Override
    public AiProductQualityValidationResponseDto validateProductQuality(AiProductQualityValidationRequestDto request) {
        return callGemini(AiRequestType.AI_RECIPE_GENERATION, contract.productQualityValidationSchema(),
                promptParts(AiPromptTemplates.productQuality(contract.writeJson(request))),
                AiProductQualityValidationResponseDto.class, maxOutputTokens());
    }

    private <T> T callGemini(AiRequestType requestType, Map<String, Object> schema,
                             List<Map<String, Object>> userParts, Class<T> responseType,
                             int outputTokenLimit) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", properties.getGemini().getApiKey());

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("maxOutputTokens", outputTokenLimit);
        generationConfig.put("responseMimeType", "application/json");
        // The shared contract is JSON Schema (including constructs such as
        // additionalProperties and union type arrays). Gemini's responseSchema
        // field expects its narrower protobuf Schema representation, whereas
        // responseJsonSchema accepts the JSON Schema contract directly.
        generationConfig.put("responseJsonSchema", schema);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("systemInstruction", Map.of("parts", List.of(textPart(
                AiPromptTemplates.SYSTEM
                        + "\nPrompt version: " + properties.getPromptVersion()
                        + "\nRequest type: " + requestType))));
        payload.put("contents", List.of(Map.of("role", "user", "parts", userParts)));
        payload.put("generationConfig", generationConfig);

        String responseBody = null;
        try {
            responseBody = restOperations.postForObject(endpoint(requestType), new HttpEntity<>(payload, headers), String.class);
            JsonNode root = objectMapper.readTree(responseBody);
            ensureComplete(root);
            String output = normalizeJsonOutput(extractOutputText(root));
            T result = objectMapper.copy()
                    .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
                    .readValue(output, responseType);
            attachUsageMetadata(result, requestType, root.path("usageMetadata"));
            return result;
        } catch (RestClientResponseException ex) {
            String error = extractProviderError(ex.getResponseBodyAsString());
            log.warn("gemini_provider_request_failed status={} error={}", ex.getStatusCode().value(), error);
            throw new AiProviderException("Gemini provider request failed: HTTP "
                    + ex.getStatusCode().value() + " - " + error);
        } catch (RestClientException ex) {
            String error = sanitize(ex.getMessage());
            log.warn("gemini_provider_request_failed transportError={}", error);
            if (causedByTimeout(ex)) {
                throw new AiProviderTimeoutException("Gemini provider request timed out.");
            }
            throw new AiProviderException("Gemini provider request failed: " + error);
        } catch (JsonProcessingException ex) {
            String error = sanitize(ex.getOriginalMessage());
            log.warn("gemini_provider_invalid_json error={}", error);
            throw new AiProviderException("Gemini provider returned an invalid JSON response: " + error);
        }
    }

    private String endpoint(AiRequestType requestType) {
        String base = properties.getGemini().getBaseUrl().replaceAll("/+$", "");
        return base + "/" + properties.resolveModel(requestType) + ":generateContent";
    }

    private int maxOutputTokens() {
        return properties.getGemini().getMaxOutputTokens();
    }

    private int geminiNutritionPlanOutputTokenBudget() {
        // Gemini counts internal reasoning against maxOutputTokens. The adaptive
        // OpenAI plan budget can therefore end before Gemini emits the complete
        // structured meal plan, even for a one-day request. Keep the configured
        // Gemini ceiling; billing is based on actual usage, not this limit.
        return maxOutputTokens();
    }

    private List<Map<String, Object>> promptParts(String prompt) {
        return List.of(textPart(prompt));
    }

    private Map<String, Object> textPart(String text) {
        return Map.of("text", text);
    }

    private Map<String, Object> inlineImagePart(String reference) {
        if (reference == null || !reference.startsWith("data:image/")) {
            return null;
        }
        int separator = reference.indexOf(";base64,");
        if (separator < 0) {
            return null;
        }
        String mimeType = reference.substring("data:".length(), separator);
        String data = reference.substring(separator + ";base64,".length());
        if (data.isBlank()) {
            return null;
        }
        return Map.of("inlineData", Map.of("mimeType", mimeType, "data", data));
    }

    private String extractOutputText(JsonNode root) {
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new AiProviderException("Gemini provider returned no candidates.");
        }
        JsonNode parts = candidates.get(0).path("content").path("parts");
        StringBuilder output = new StringBuilder();
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                if (part.path("text").isTextual()) {
                    output.append(part.path("text").asText());
                }
            }
        }
        if (output.isEmpty()) {
            throw new AiProviderException("Gemini provider returned no JSON output text.");
        }
        return output.toString();
    }

    private void ensureComplete(JsonNode root) {
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            return;
        }
        String finishReason = candidates.get(0).path("finishReason").asText("");
        if (!finishReason.isBlank() && !"STOP".equalsIgnoreCase(finishReason)) {
            throw new AiProviderException("Gemini provider returned an incomplete response: " + sanitize(finishReason));
        }
    }

    private String normalizeJsonOutput(String output) {
        String trimmed = output == null ? "" : output.trim();
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            trimmed = trimmed.substring(3, trimmed.length() - 3).trim();
            if (trimmed.regionMatches(true, 0, "json", 0, 4)) {
                trimmed = trimmed.substring(4).trim();
            }
        }
        return trimmed;
    }

    private void attachUsageMetadata(Object result, AiRequestType requestType, JsonNode usage) {
        if (!(result instanceof AiUsageMetadataCarrier carrier)) {
            return;
        }
        Integer input = integerOrNull(usage.path("promptTokenCount"));
        Integer total = integerOrNull(usage.path("totalTokenCount"));
        Integer output;
        if (total != null && input != null) {
            output = Math.max(0, total - input);
        } else {
            output = integerOrNull(usage.path("candidatesTokenCount"));
        }
        carrier.setPromptTokens(input);
        carrier.setCompletionTokens(output);
        Integer effectiveTotal = total;
        if (effectiveTotal == null && (input != null || output != null)) {
            effectiveTotal = (input == null ? 0 : input) + (output == null ? 0 : output);
        }
        carrier.setTotalTokens(effectiveTotal);
        carrier.setEstimatedCost(estimateCost(requestType, input, output));
        carrier.setCostCurrency(costCurrency(requestType));
    }

    private Integer integerOrNull(JsonNode node) {
        return node != null && node.isNumber() ? node.asInt() : null;
    }

    private Double estimateCost(AiRequestType requestType, Integer input, Integer output) {
        boolean photo = requestType == AiRequestType.PHOTO_MEAL_LOG;
        double inputRate = photo
                ? properties.getPhoto().getInputTokenCostPer1m()
                : properties.getGemini().getInputTokenCostPer1m();
        double outputRate = photo
                ? properties.getPhoto().getOutputTokenCostPer1m()
                : properties.getGemini().getOutputTokenCostPer1m();
        if ((input == null && output == null) || (inputRate <= 0 && outputRate <= 0)) {
            return null;
        }
        return ((input == null ? 0 : input) * inputRate
                + (output == null ? 0 : output) * outputRate) / 1_000_000d;
    }

    private String costCurrency(AiRequestType requestType) {
        return requestType == AiRequestType.PHOTO_MEAL_LOG
                ? properties.getPhoto().getCostCurrency()
                : properties.getGemini().getCostCurrency();
    }

    private String extractProviderError(String body) {
        if (body == null || body.isBlank()) {
            return "provider returned an empty error response";
        }
        try {
            JsonNode error = objectMapper.readTree(body).path("error");
            String status = error.path("status").asText("");
            String message = error.path("message").asText("");
            return sanitize((status.isBlank() ? "" : status + ": ") + message);
        } catch (JsonProcessingException ignored) {
            return sanitize(body);
        }
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "provider request failed without details";
        }
        String sanitized = value
                .replaceAll("AIza[A-Za-z0-9_\\-]+", "AIza***")
                .replaceAll("x-goog-api-key[=:]\\s*[^\\s,;]+", "x-goog-api-key=***")
                .trim();
        return sanitized.length() > 700 ? sanitized.substring(0, 700) : sanitized;
    }

    private boolean causedByTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String name = current.getClass().getSimpleName();
            if (name.contains("Timeout") || name.contains("TimedOut")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
