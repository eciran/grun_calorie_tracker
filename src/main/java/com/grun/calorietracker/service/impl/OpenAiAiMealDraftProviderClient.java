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
import com.grun.calorietracker.dto.MealPlanNutritionSnapshotDto;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.exception.AiProviderException;
import com.grun.calorietracker.service.AiMealDraftProviderClient;
import com.grun.calorietracker.service.prompt.AiPromptTemplates;
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

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
public class OpenAiAiMealDraftProviderClient implements AiMealDraftProviderClient {


    private final AiProperties properties;
    private final RestOperations restOperations;
    private final ObjectMapper objectMapper;

    @Autowired
    public OpenAiAiMealDraftProviderClient(AiProperties properties, RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        this(properties, restTemplateBuilder
                .setConnectTimeout(properties.getOpenai().getConnectTimeout())
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
        return callOpenAi(AiRequestType.VOICE_FOOD_LOG, "grun_meal_draft", mealDraftSchema(false), List.of(textContent(AiPromptTemplates.request(AiRequestType.VOICE_FOOD_LOG, writeJson(request)))), AiMealDraftResponseDto.class);
    }

    @Override
    public AiMealDraftResponseDto createPhotoMealDraft(AiPhotoMealDraftRequestDto request) {
        List<Map<String, Object>> content = new ArrayList<>();
        boolean alternativeSnapshotsEnabled = properties.getPhoto().isAlternativeSnapshotsEnabled();
        int maxAlternatives = properties.getPhoto().getMaxAlternativeSnapshots();
        content.add(textContent(AiPromptTemplates.photo(writeJson(request), alternativeSnapshotsEnabled, maxAlternatives)));
        content.add(textContent(AiPromptTemplates.PHOTO_PORTION_RULES));
        String imageReference = resolveOpenAiImageReference(request.getImageReference());
        if (isOpenAiImageReference(imageReference)) {
            content.add(imageContent(imageReference));
        } else {
            content.add(textContent("Image reference is not directly accessible by OpenAI. Return a cautious draft from metadata only and require review."));
        }
        return callOpenAi(AiRequestType.PHOTO_MEAL_LOG,
                alternativeSnapshotsEnabled ? "grun_meal_draft_v4" : "grun_meal_draft",
                mealDraftSchema(alternativeSnapshotsEnabled), content, AiMealDraftResponseDto.class);
    }

    @Override
    public AiRecipeDraftResponseDto createRecipeDraft(AiRecipeDraftRequestDto request) {
        return callOpenAi(AiRequestType.AI_RECIPE_GENERATION, "grun_recipe_draft", recipeDraftSchema(), List.of(textContent(AiPromptTemplates.request(AiRequestType.AI_RECIPE_GENERATION, writeJson(request)))), AiRecipeDraftResponseDto.class);
    }

    @Override
    public AiPreparationGuideResponseDto createPreparationGuide(AiPreparationGuideProviderRequestDto request) {
        return callOpenAi(
                AiRequestType.AI_MEAL_PREPARATION_GUIDE,
                "grun_preparation_guide_v1",
                preparationGuideSchema(),
                List.of(textContent(AiPromptTemplates.request(AiRequestType.AI_MEAL_PREPARATION_GUIDE, writeJson(request)))),
                AiPreparationGuideResponseDto.class);
    }
    @Override
    public AiNutritionPlanDraftResponseDto createNutritionPlanDraft(AiNutritionPlanDraftRequestDto request) {
        String targetGuardrails = nutritionPlanTargetGuardrails(request);
        return callOpenAi(
                AiRequestType.AI_NUTRITION_PLAN,
                "grun_nutrition_plan_v1",
                nutritionPlanSchema(),
                List.of(textContent(AiPromptTemplates.nutrition(targetGuardrails, writeJson(request)))),
                AiNutritionPlanDraftResponseDto.class,
                nutritionPlanOutputTokenBudget(request)
        );
    }

    private String nutritionPlanTargetGuardrails(AiNutritionPlanDraftRequestDto request) {
        if (request == null || request.getTrustedDailyTarget() == null) {
            return "";
        }
        MealPlanNutritionSnapshotDto target = request.getTrustedDailyTarget();
        return "Backend-computed daily target guardrails: "
                + targetRange("calories", target.getCalories(), 100.0, 0.15, 150.0, 0.25)
                + targetRange("protein", target.getProtein(), 20.0, 0.20, 40.0, 0.45)
                + targetRange("carbohydrates", target.getCarbs(), 30.0, 0.20, 70.0, 0.45)
                + targetRange("fat", target.getFat(), 15.0, 0.20, 30.0, 0.60)
                + "Build item portions so their computed daily sums stay inside every preferred range. "
                + "A value outside a hard range is unusable and will be rejected. ";
    }

    private String targetRange(String name, Double target, double preferredMinimum,
                               double preferredRatio, double hardMinimum, double hardRatio) {
        if (target == null || !Double.isFinite(target) || target < 0) {
            return "";
        }
        double preferredTolerance = Math.max(preferredMinimum, target * preferredRatio);
        double hardTolerance = Math.max(hardMinimum, target * hardRatio);
        return String.format(Locale.ROOT,
                "%s target=%.1f preferredRange=%.1f..%.1f hardRange=%.1f..%.1f; ",
                name, target, Math.max(0, target - preferredTolerance), target + preferredTolerance,
                Math.max(0, target - hardTolerance), target + hardTolerance);
    }

    @Override
    public AiWorkoutPlanDraftResponseDto createWorkoutPlanDraft(AiWorkoutPlanDraftRequestDto request) {
        return callOpenAi(AiRequestType.AI_WORKOUT_PLAN, "grun_workout_plan_draft", workoutPlanSchema(), List.of(textContent(AiPromptTemplates.request(AiRequestType.AI_WORKOUT_PLAN, writeJson(request)))), AiWorkoutPlanDraftResponseDto.class);
    }

    @Override
    public AiInsightResponseDto createDailyInsight(AiInsightRequestDto request) {
        return callOpenAi(AiRequestType.AI_DAILY_INSIGHT, "grun_insight", insightSchema(), List.of(textContent(AiPromptTemplates.request(AiRequestType.AI_DAILY_INSIGHT, writeJson(request)))), AiInsightResponseDto.class);
    }

    @Override
    public AiInsightResponseDto createWeeklyInsight(AiInsightRequestDto request) {
        return callOpenAi(AiRequestType.AI_WEEKLY_INSIGHT, "grun_insight", insightSchema(), List.of(textContent(AiPromptTemplates.request(AiRequestType.AI_WEEKLY_INSIGHT, writeJson(request)))), AiInsightResponseDto.class);
    }

    private <T> T callOpenAi(AiRequestType requestType, String schemaName, Map<String, Object> schema, List<Map<String, Object>> userContent, Class<T> responseType) {
        return callOpenAi(requestType, schemaName, schema, userContent, responseType,
                properties.getOpenai().getMaxOutputTokens());
    }

    private <T> T callOpenAi(AiRequestType requestType, String schemaName, Map<String, Object> schema,
                             List<Map<String, Object>> userContent, Class<T> responseType,
                             int maxOutputTokens) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getOpenai().getApiKey());

        Map<String, Object> payload = buildProviderPayload(
                schemaName,
                schema,
                List.of(
                        message("system", List.of(textContent(AiPromptTemplates.SYSTEM
                                + "\nPrompt version: " + properties.getPromptVersion()
                                + "\nRequest type: " + requestType))),
                        message("user", userContent)
                ),
                maxOutputTokens
        );

        String responseBody = null;
        String outputText = null;
        try {
            responseBody = postProviderRequest(payload, headers);
            JsonNode root = objectMapper.readTree(responseBody);
            outputText = normalizeJsonOutput(extractOutputText(root));
            try {
                T result = readProviderOutput(outputText, responseType);
                attachUsageMetadata(result, root.path("usage"));
                return result;
            } catch (JsonProcessingException parseException) {
                return repairInvalidOutput(
                        requestType,
                        schemaName,
                        schema,
                        outputText,
                        parseException,
                        responseType,
                        headers,
                        root.path("usage"),
                        maxOutputTokens
                );
            }
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

    private Map<String, Object> buildProviderPayload(
            String schemaName,
            Map<String, Object> schema,
            List<Map<String, Object>> input
    ) {
        return buildProviderPayload(schemaName, schema, input,
                properties.getOpenai().getMaxOutputTokens());
    }

    private Map<String, Object> buildProviderPayload(
            String schemaName,
            Map<String, Object> schema,
            List<Map<String, Object>> input,
            int maxOutputTokens
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("store", false);
        payload.put("input", input);
        if (maxOutputTokens > 0) {
            payload.put("max_output_tokens", maxOutputTokens);
        }
        payload.put("text", Map.of("format", Map.of(
                "type", "json_schema",
                "name", schemaName,
                "strict", true,
                "schema", schema
        )));
        return payload;
    }

    private String postProviderRequest(Map<String, Object> payload, HttpHeaders headers) {
        return restOperations.postForObject(
                properties.getOpenai().getBaseUrl(),
                new HttpEntity<>(payload, headers),
                String.class
        );
    }

    private <T> T readProviderOutput(String outputText, Class<T> responseType) throws JsonProcessingException {
        return objectMapper.copy()
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
                .readValue(outputText, responseType);
    }

    private <T> T repairInvalidOutput(
            AiRequestType requestType,
            String schemaName,
            Map<String, Object> schema,
            String invalidOutput,
            JsonProcessingException originalException,
            Class<T> responseType,
            HttpHeaders headers,
            JsonNode primaryUsage,
            int maxOutputTokens
    ) {
        String parseError = sanitizeError(originalException.getOriginalMessage());
        int configuredAttempts = properties.getOpenai().getMaxRepairAttempts();
        if (!properties.getOpenai().isRepairEnabled() || configuredAttempts < 1) {
            throw invalidJsonException(parseError, invalidOutput, null);
        }

        int maxAttempts = Math.min(configuredAttempts, 2);
        if (configuredAttempts > maxAttempts) {
            log.warn("openai_provider_json_repair_attempts_capped configured={} effective={}",
                    configuredAttempts, maxAttempts);
        }

        String candidate = invalidOutput;
        String lastResponseBody = null;
        List<JsonNode> usages = new ArrayList<>();
        usages.add(primaryUsage);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            log.warn("openai_provider_json_repair_started requestType={} promptVersion={} attempt={} maxAttempts={} error={}",
                    requestType,
                    properties.getPromptVersion(),
                    attempt,
                    maxAttempts,
                    parseError);

            Map<String, Object> repairPayload = buildProviderPayload(
                    schemaName + "_repair",
                    schema,
                    List.of(
                            message("system", List.of(textContent(AiPromptTemplates.REPAIR_SYSTEM))),
                            message("user", List.of(textContent(
                                    AiPromptTemplates.repairUser(requestType, parseError, candidate))))
                    ),
                    maxOutputTokens
            );

            String repairedOutput = null;
            try {
                lastResponseBody = postProviderRequest(repairPayload, headers);
                JsonNode repairRoot = objectMapper.readTree(lastResponseBody);
                usages.add(repairRoot.path("usage"));
                repairedOutput = normalizeJsonOutput(extractOutputText(repairRoot));
                T result = readProviderOutput(repairedOutput, responseType);
                attachUsageMetadata(result, usages.toArray(JsonNode[]::new));
                log.info("openai_provider_json_repair_succeeded requestType={} promptVersion={} attempt={}",
                        requestType,
                        properties.getPromptVersion(),
                        attempt);
                return result;
            } catch (JsonProcessingException ex) {
                parseError = sanitizeError(ex.getOriginalMessage());
                candidate = repairedOutput == null ? lastResponseBody : repairedOutput;
            }
        }

        throw invalidJsonException(parseError, candidate, lastResponseBody);
    }

    private AiProviderException invalidJsonException(String error, String outputText, String responseBody) {
        log.warn("openai_provider_invalid_json error={} outputSnippet={} responseSnippet={}",
                error,
                sanitizeSnippet(outputText),
                sanitizeSnippet(responseBody));
        return new AiProviderException("OpenAI provider returned an invalid JSON response: " + error);
    }

    private void attachUsageMetadata(Object result, JsonNode... usages) {
        if (!(result instanceof AiUsageMetadataCarrier carrier)) {
            return;
        }
        Integer inputTokens = sumUsageTokens(usages, "input_tokens");
        Integer outputTokens = sumUsageTokens(usages, "output_tokens");
        Integer totalTokens = sumUsageTokens(usages, "total_tokens");
        if (totalTokens == null && (inputTokens != null || outputTokens != null)) {
            totalTokens = (inputTokens == null ? 0 : inputTokens)
                    + (outputTokens == null ? 0 : outputTokens);
        }
        carrier.setPromptTokens(inputTokens);
        carrier.setCompletionTokens(outputTokens);
        carrier.setTotalTokens(totalTokens);
        carrier.setEstimatedCost(estimateCost(inputTokens, outputTokens));
        carrier.setCostCurrency(properties.getOpenai().getCostCurrency());
    }

    private Integer sumUsageTokens(JsonNode[] usages, String field) {
        int total = 0;
        boolean found = false;
        if (usages != null) {
            for (JsonNode usage : usages) {
                Integer value = usage == null ? null : integerOrNull(usage.path(field));
                if (value != null) {
                    total += value;
                    found = true;
                }
            }
        }
        return found ? total : null;
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


    private String resolveOpenAiImageReference(String imageReference) {
        String managedToken = managedPhotoReferenceToken(imageReference);
        if (managedToken == null) {
            return imageReference;
        }
        Path target = Path.of(properties.getPhoto().getStorageDirectory()).toAbsolutePath().normalize()
                .resolve(managedToken)
                .normalize();
        Path storageRoot = Path.of(properties.getPhoto().getStorageDirectory()).toAbsolutePath().normalize();
        if (!target.startsWith(storageRoot) || !Files.isRegularFile(target)) {
            return imageReference;
        }
        try {
            byte[] bytes = Files.readAllBytes(target);
            if (bytes.length == 0 || bytes.length > properties.getPhoto().getMaxUploadBytes()) {
                return imageReference;
            }
            return "data:" + contentTypeFromToken(managedToken) + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException ex) {
            return imageReference;
        }
    }

    private String managedPhotoReferenceToken(String imageReference) {
        if (imageReference == null || imageReference.isBlank()) {
            return null;
        }
        try {
            URI reference = URI.create(imageReference.trim());
            URI publicBase = URI.create(properties.getPhoto().getPublicBaseUrl());
            if (!equalsIgnoreCase(reference.getScheme(), publicBase.getScheme())
                    || !equalsIgnoreCase(reference.getHost(), publicBase.getHost())
                    || effectivePort(reference) != effectivePort(publicBase)
                    || reference.getRawQuery() != null
                    || reference.getRawFragment() != null) {
                return null;
            }
            String basePath = publicBase.getPath() == null ? "" : publicBase.getPath().replaceAll("/+$", "");
            String requiredPath = basePath + "/api/v1/ai/meal-drafts/photo-references/";
            String referencePath = reference.getPath();
            if (referencePath == null || !referencePath.startsWith(requiredPath)) {
                return null;
            }
            String token = URLDecoder.decode(referencePath.substring(requiredPath.length()), StandardCharsets.UTF_8);
            if (token.isBlank() || token.contains("..") || token.contains("/") || token.contains("\\")) {
                return null;
            }
            return token;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String contentTypeFromToken(String token) {
        String normalized = token == null ? "" : token.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (normalized.endsWith(".png")) {
            return "image/png";
        }
        if (normalized.endsWith(".webp")) {
            return "image/webp";
        }
        throw new IllegalArgumentException("Unsupported managed AI photo format.");
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private Map<String, Object> imageContent(String imageReference) {
        return Map.of(
                "type", "input_image",
                "image_url", imageReference,
                "detail", "high");
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

    private Map<String, Object> mealDraftSchema(boolean alternativeSnapshotsEnabled) {
        Map<String, Object> itemProperties = props(
                "name", stringSchema(),
                "quantity", numberSchema(),
                "unit", enumSchema("GRAM", "MILLILITER", "TABLESPOON", "TEASPOON", "SLICE", "SERVING", "PIECE"),
                "detectedPieceCount", integerSchema(),
                "estimatedTotalWeightGrams", numberSchema(),
                "estimatedCalories", numberSchema(),
                "estimatedProtein", numberSchema(),
                "estimatedCarbs", numberSchema(),
                "estimatedFat", numberSchema(),
                "estimatedNutrition", nutritionSchema(),
                "nutritionEstimateNote", stringSchema(),
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
        );
        if (alternativeSnapshotsEnabled) {
            itemProperties.put("alternativeCandidates", arraySchema(strictObjectSchema(props(
                    "name", stringSchema(),
                    "quantity", numberSchema(),
                    "unit", enumSchema("GRAM", "MILLILITER", "TABLESPOON", "TEASPOON", "SLICE", "SERVING", "PIECE"),
                    "detectedPieceCount", integerSchema(),
                    "estimatedTotalWeightGrams", numberSchema(),
                    "estimatedNutrition", nutritionSchema(),
                    "nutritionEstimateNote", stringSchema(),
                    "matchReason", stringSchema(),
                    "confidence", numberSchema(),
                    "materiallyDifferent", booleanSchema()
            ))));
        }
        return strictObjectSchema(props(
                "schemaVersion", enumSchema(alternativeSnapshotsEnabled ? "ai_response_v4" : "ai_response_v3"),
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
                "items", arraySchema(strictObjectSchema(itemProperties))
        ));
    }

    private Map<String, Object> recipeDraftSchema() {
        return strictObjectSchema(props(
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
                "suggestedRecipe", strictObjectSchema(props(
                        "name", stringSchema(),
                        "description", stringSchema(),
                        "mealType", stringSchema(),
                        "language", stringSchema(),
                        "totalYieldGrams", numberSchema(),
                        "defaultServingGrams", numberSchema(),
                        "servingCount", integerSchema(),
                        "categories", arraySchema(enumSchema("VEGAN", "VEGETARIAN", "HIGH_PROTEIN", "LOW_CARB", "LOW_FAT", "LOW_CALORIE", "HIGH_FIBER", "GLUTEN_FREE", "DAIRY_FREE", "BREAKFAST", "LUNCH", "DINNER", "SNACK", "VEGETABLES", "MEAT", "CHICKEN", "FISH", "SOUP", "SALAD", "DESSERT", "QUICK_MEAL", "MEAL_PREP", "TURKISH", "MEDITERRANEAN", "UK_IE")),
                        "allergens", arraySchema(enumSchema("MILK", "EGGS", "FISH", "CRUSTACEAN_SHELLFISH", "TREE_NUTS", "PEANUTS", "WHEAT", "SOYBEANS", "SESAME", "GLUTEN", "CELERY", "MUSTARD", "LUPIN", "MOLLUSCS", "SULPHITES")),
                        "cookingSteps", arraySchema(strictObjectSchema(props(
                                "instruction", stringSchema()
                        )))
                )),
                "estimatedNutritionTotal", nutritionSchema(),
                "estimatedNutritionPerServing", nutritionSchema(),
                "nutritionEstimateNote", stringSchema(),
                "suggestedIngredients", arraySchema(strictObjectSchema(props(
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
        return strictObjectSchema(props(
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


    private Map<String, Object> preparationGuideSchema() {
        return strictObjectSchema(props(
                "preparationMinutes", integerSchema(),
                "cookingMinutes", integerSchema(),
                "equipment", arraySchema(stringSchema()),
                "ingredients", arraySchema(strictObjectSchema(props(
                        "name", stringSchema(),
                        "quantity", numberSchema(),
                        "unit", enumSchema("GRAM", "MILLILITER", "TABLESPOON", "TEASPOON", "SLICE", "SERVING", "PIECE"),
                        "optional", booleanSchema(),
                        "changesPlannedNutrition", booleanSchema()
                ))),
                "steps", arraySchema(strictObjectSchema(props(
                        "stepNumber", integerSchema(),
                        "instruction", stringSchema(),
                        "durationMinutes", integerSchema(),
                        "temperatureCelsius", numberSchema()
                ))),
                "foodSafetyNotes", arraySchema(stringSchema()),
                "storageInstructions", arraySchema(stringSchema()),
                "substitutions", arraySchema(strictObjectSchema(props(
                        "originalIngredient", stringSchema(),
                        "substitute", stringSchema(),
                        "changesPlannedNutrition", booleanSchema(),
                        "nutritionImpactWarning", stringSchema()
                ))),
                "nutritionImpactWarnings", arraySchema(stringSchema()),
                "assumptions", arraySchema(stringSchema()),
                "reviewRequired", booleanSchema(),
                "qualityScore", integerSchema(),
                "confidence", numberSchema(),
                "estimatedUncertainty", enumSchema("LOW", "MEDIUM", "HIGH")
        ));
    }
    private Map<String, Object> nutritionPlanSchema() {
        Map<String, Object> item = strictObjectSchema(props(
                "displayName", stringSchema(),
                "quantity", numberSchema(),
                "unit", enumSchema("GRAM", "MILLILITER", "TABLESPOON", "TEASPOON", "SLICE", "SERVING", "PIECE"),
                "nutrition", coreNutritionSchema(),
                "allergens", arraySchema(stringSchema()),
                "shortPreparationState", stringSchema(),
                "workoutRelation", enumSchema("NONE", "PRE_WORKOUT", "POST_WORKOUT", "RECOVERY")
        ));
        Map<String, Object> meal = strictObjectSchema(props(
                "mealType", enumSchema("BREAKFAST", "LUNCH", "DINNER", "SNACK"),
                "suggestedTime", stringSchema(),
                "summary", stringSchema(),
                "items", arraySchema(item)
        ));
        Map<String, Object> day = strictObjectSchema(props(
                "date", stringSchema(),
                "meals", arraySchema(meal),
                "dailyMicronutrients", dailyMicronutritionSchema()
        ));
        return strictObjectSchema(props(
                "name", stringSchema(),
                "summary", stringSchema(),
                "professionalSummary", stringSchema(),
                "days", arraySchema(day),
                "assumptions", arraySchema(stringSchema()),
                "warnings", arraySchema(stringSchema()),
                "nextBestActions", arraySchema(stringSchema()),
                "confidence", numberSchema(),
                "qualityScore", integerSchema(),
                "estimatedUncertainty", enumSchema("LOW", "MEDIUM", "HIGH")
        ));
    }

    private Map<String, Object> dailyMicronutritionSchema() {
        return strictObjectSchema(props(
                "sodium", numberSchema(),
                "potassium", numberSchema(),
                "calcium", numberSchema(),
                "iron", numberSchema(),
                "magnesium", numberSchema(),
                "zinc", numberSchema(),
                "vitaminC", numberSchema(),
                "vitaminD", numberSchema(),
                "vitaminB12", numberSchema()
        ));
    }

    private Map<String, Object> coreNutritionSchema() {
        return strictObjectSchema(props(
                "calories", numberSchema(),
                "protein", numberSchema(),
                "carbs", numberSchema(),
                "fat", numberSchema(),
                "fiber", numberSchema()
        ));
    }

    private int nutritionPlanOutputTokenBudget(AiNutritionPlanDraftRequestDto request) {
        int configuredLimit = properties.getOpenai().getMaxOutputTokens();
        if (configuredLimit <= 0) {
            return configuredLimit;
        }
        int days = request == null || request.getDayCount() == null
                ? 1 : Math.max(1, request.getDayCount());
        int meals = request == null || request.getMealsPerDay() == null
                ? 4 : Math.max(1, request.getMealsPerDay());
        int estimatedBudget = 1_800 + (days * meals * 160);
        return Math.min(configuredLimit, Math.max(3_000, estimatedBudget));
    }
    private Map<String, Object> workoutPlanSchema() {
        return strictObjectSchema(props(
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
                "days", arraySchema(strictObjectSchema(props(
                        "dayLabel", stringSchema(),
                        "focus", stringSchema(),
                        "estimatedDurationMinutes", integerSchema(),
                        "warmup", stringSchema(),
                        "cooldown", stringSchema(),
                        "exercises", arraySchema(strictObjectSchema(props(
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
        return callOpenAi(AiRequestType.AI_RECIPE_GENERATION, "grun_product_quality_validation_v3", productQualityValidationSchema(), List.of(textContent(AiPromptTemplates.productQuality(writeJson(request)))), AiProductQualityValidationResponseDto.class);
    }


    private Map<String, Object> productQualityValidationSchema() {
        return strictObjectSchema(props(
                "schemaVersion", enumSchema("product_quality_response_v2"),
                "summary", stringSchema(),
                "confidence", numberSchema(),
                "qualityScore", integerSchema(),
                "reviewRequired", booleanSchema(),
                "issues", arraySchema(strictObjectSchema(props(
                        "suggestionType", enumSchema(
                                "NAME_CLEANUP",
                                "DISPLAY_NAME",
                                "LOCALIZATION",
                                "SEARCH_ALIAS",
                                "SERVING_OPTION",
                                "CANONICAL_DUPLICATE_REVIEW",
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
        return strictObjectSchema(props(
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
                "dataCoverage", strictObjectSchema(props(
                        "daysAnalyzed", integerSchema(),
                        "mealsLogged", integerSchema(),
                        "exerciseLogged", booleanSchema(),
                        "exerciseMinutes", integerSchema(),
                        "diaryDays", integerSchema(),
                        "signalsUsed", arraySchema(stringSchema()),
                        "missingSignals", arraySchema(stringSchema()),
                        "confidenceLabel", enumSchema("LOW", "MEDIUM", "HIGH")
                )),
                "keyFindings", arraySchema(strictObjectSchema(props(
                        "type", enumSchema("trend", "pattern", "risk", "quality", "consistency", "opportunity"),
                        "label", stringSchema(),
                        "message", stringSchema(),
                        "evidence", stringSchema(),
                        "impact", stringSchema(),
                        "severity", enumSchema("LOW", "MEDIUM", "HIGH")
                ))),
                "personalizedActions", arraySchema(strictObjectSchema(props(
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

    private Map<String, Object> strictObjectSchema(Map<String, Object> properties) {
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

