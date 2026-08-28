package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.config.ProductNutritionOcrProperties;
import com.grun.calorietracker.exception.AiProviderException;
import com.grun.calorietracker.service.ProductNutritionOcrProvider;
import com.grun.calorietracker.service.model.ProductNutritionOcrEvidence;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackRequest;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackResult;
import com.grun.calorietracker.service.model.ProductNutritionOcrPreparedEvidence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GeminiProductNutritionOcrProvider implements ProductNutritionOcrProvider {
    private static final String PROVIDER = "GEMINI";
    private static final Logger log = LoggerFactory.getLogger(GeminiProductNutritionOcrProvider.class);
    private static final String SYSTEM_PROMPT = """
            You extract nutrition-label evidence. Never guess or infer a missing value.
            Return null for unreadable rawValue, unit, basis or evidenceBox values.
            Preserve the printed raw value and unit. Basis must be one of PER_100G,
            PER_100ML, PER_SERVING, RI_PERCENT or UNKNOWN. Every non-null value must
            include a normalized evidenceBox from the visible label.
            """;

    private final AiProperties sharedAiProperties;
    private final ProductNutritionOcrProperties properties;
    private final RestOperations restOperations;
    private final ObjectMapper objectMapper;
    private final ProductNutritionOcrImagePreprocessor imagePreprocessor;
    private final ProductNutritionOcrResultValidator resultValidator;

    @Autowired
    public GeminiProductNutritionOcrProvider(
            AiProperties sharedAiProperties,
            ProductNutritionOcrProperties properties,
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper,
            ProductNutritionOcrImagePreprocessor imagePreprocessor,
            ProductNutritionOcrResultValidator resultValidator) {
        this(sharedAiProperties, properties, restTemplateBuilder
                .setConnectTimeout(sharedAiProperties.getGemini().getConnectTimeout())
                .setReadTimeout(sharedAiProperties.getGemini().getTimeout())
                .build(), objectMapper, imagePreprocessor, resultValidator);
    }

    public GeminiProductNutritionOcrProvider(
            AiProperties sharedAiProperties,
            ProductNutritionOcrProperties properties,
            RestOperations restOperations,
            ObjectMapper objectMapper,
            ProductNutritionOcrImagePreprocessor imagePreprocessor,
            ProductNutritionOcrResultValidator resultValidator) {
        this.sharedAiProperties = sharedAiProperties;
        this.properties = properties;
        this.restOperations = restOperations;
        this.objectMapper = objectMapper;
        this.imagePreprocessor = imagePreprocessor;
        this.resultValidator = resultValidator;
    }

    @Override
    public String providerId() {
        return PROVIDER;
    }

    @Override
    public ProductNutritionOcrFallbackResult analyze(
            ProductNutritionOcrFallbackRequest request,
            ProductNutritionOcrEvidence evidence) {
        ProductNutritionOcrPreparedEvidence prepared = imagePreprocessor.prepare(evidence, request.wordBoxes());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", sharedAiProperties.getGemini().getApiKey());
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseJsonSchema", responseSchema());
        generationConfig.put("maxOutputTokens", 2048);
        generationConfig.put("temperature", 0);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("systemInstruction", Map.of("parts", List.of(textPart(SYSTEM_PROMPT))));
        payload.put("contents", List.of(Map.of("role", "user", "parts", List.of(
                textPart(userPrompt(request, prepared.wordBoxes())),
                Map.of("inlineData", Map.of(
                        "mimeType", prepared.evidence().contentType(),
                        "data", Base64.getEncoder().encodeToString(prepared.evidence().bytes())))
        ))));
        payload.put("generationConfig", generationConfig);
        try {
            String body = postWithRetry(payload, headers, request);
            JsonNode root = objectMapper.readTree(body);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new AiProviderException("Gemini product OCR returned no candidates.");
            }
            String finishReason = candidates.get(0).path("finishReason").asText("");
            if (!finishReason.isBlank() && !"STOP".equalsIgnoreCase(finishReason)) {
                throw new AiProviderException("Gemini product OCR returned an incomplete response: " + finishReason);
            }
            String output = candidates.get(0).path("content").path("parts").path(0).path("text").asText("");
            GeminiResponse response = objectMapper.readValue(output, GeminiResponse.class);
            if (response.fields() == null) {
                throw new AiProviderException("Gemini product OCR returned an incompatible schema.");
            }
            Map<String, Object> fields = new LinkedHashMap<>();
            for (GeminiField field : response.fields()) {
                if (field == null || field.name() == null || field.name().isBlank()) {
                    throw new AiProviderException("Gemini product OCR returned an incompatible schema.");
                }
                fields.put(field.name(), objectMapper.convertValue(field, Map.class));
            }
            return new ProductNutritionOcrFallbackResult(PROVIDER, properties.getGeminiModel(),
                    resultValidator.validate(request, fields));
        } catch (JsonProcessingException exception) {
            throw new AiProviderException("Gemini product OCR returned an incompatible schema.");
        }
    }

    private String postWithRetry(
            Map<String, Object> payload,
            HttpHeaders headers,
            ProductNutritionOcrFallbackRequest request) {
        int maxAttempts = properties.getMaxAttempts();
        long startedAt = System.nanoTime();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String response = restOperations.postForObject(
                        endpoint(), new HttpEntity<>(payload, headers), String.class);
                log.info("product_ocr_provider provider={} caseId={} assetId={} attempt={} maxAttempts={} durationMs={} correlationId={}",
                        PROVIDER, request.reviewCaseId(), request.nutritionAssetId(), attempt, maxAttempts,
                        elapsedMillis(startedAt), MDC.get("correlationId"));
                return response;
            } catch (RestClientResponseException exception) {
                int status = exception.getStatusCode().value();
                boolean retryable = status == 429 || status >= 500;
                log.warn("product_ocr_provider_failed provider={} caseId={} assetId={} attempt={} maxAttempts={} status={} retryable={} durationMs={} correlationId={}",
                        PROVIDER, request.reviewCaseId(), request.nutritionAssetId(), attempt, maxAttempts,
                        status, retryable, elapsedMillis(startedAt), MDC.get("correlationId"));
                if (!retryable || attempt == maxAttempts) {
                    throw new AiProviderException("Gemini product OCR request failed: HTTP " + status);
                }
            } catch (RestClientException exception) {
                log.warn("product_ocr_provider_failed provider={} caseId={} assetId={} attempt={} maxAttempts={} status=transport retryable={} durationMs={} correlationId={}",
                        PROVIDER, request.reviewCaseId(), request.nutritionAssetId(), attempt, maxAttempts,
                        attempt < maxAttempts, elapsedMillis(startedAt), MDC.get("correlationId"));
                if (attempt == maxAttempts) {
                    throw new AiProviderException("Gemini product OCR request failed.");
                }
            }
        }
        throw new AiProviderException("Gemini product OCR request failed.");
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String endpoint() {
        String base = sharedAiProperties.getGemini().getBaseUrl().replaceAll("/+$", "");
        return base + "/" + properties.getGeminiModel() + ":generateContent";
    }

    private String userPrompt(ProductNutritionOcrFallbackRequest request, List<Map<String, Object>> wordBoxes) {
        try {
            return "Analyze only these uncertain fields: " + objectMapper.writeValueAsString(request.uncertainFields())
                    + ". Use these local OCR word boxes as supporting evidence: "
                    + objectMapper.writeValueAsString(wordBoxes)
                    + ". Do not return fields outside the uncertain field list.";
        } catch (JsonProcessingException exception) {
            throw new AiProviderException("Product nutrition OCR evidence could not be encoded.");
        }
    }

    private Map<String, Object> responseSchema() {
        Map<String, Object> box = Map.of(
                "type", "object",
                "properties", Map.of(
                        "x", Map.of("type", List.of("number", "null")),
                        "y", Map.of("type", List.of("number", "null")),
                        "width", Map.of("type", List.of("number", "null")),
                        "height", Map.of("type", List.of("number", "null"))));
        Map<String, Object> field = Map.of(
                "type", "object",
                "required", List.of("name", "rawValue", "unit", "basis", "evidenceBox"),
                "properties", Map.of(
                        "name", Map.of("type", "string"),
                        "rawValue", Map.of("type", List.of("string", "null")),
                        "unit", Map.of("type", List.of("string", "null")),
                        "basis", Map.of("anyOf", List.of(
                                Map.of("type", "string", "enum",
                                        List.of("PER_100G", "PER_100ML", "PER_SERVING", "RI_PERCENT", "UNKNOWN")),
                                Map.of("type", "null"))),
                        "evidenceBox", Map.of("anyOf", List.of(box, Map.of("type", "null")))));
        return Map.of(
                "type", "object",
                "required", List.of("fields"),
                "properties", Map.of("fields", Map.of("type", "array", "items", field)));
    }

    private Map<String, Object> textPart(String text) {
        return Map.of("text", text);
    }

    private record GeminiResponse(List<GeminiField> fields) { }

    private record GeminiField(
            String name,
            String rawValue,
            String unit,
            String basis,
            Map<String, Double> evidenceBox) { }
}
