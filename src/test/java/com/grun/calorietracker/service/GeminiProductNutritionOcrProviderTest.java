package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.config.ProductNutritionOcrProperties;
import com.grun.calorietracker.service.impl.GeminiProductNutritionOcrProvider;
import com.grun.calorietracker.service.impl.ProductNutritionOcrImagePreprocessor;
import com.grun.calorietracker.service.impl.ProductNutritionOcrResultValidator;
import com.grun.calorietracker.service.model.ProductNutritionOcrEvidence;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackRequest;
import com.grun.calorietracker.exception.AiProviderException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class GeminiProductNutritionOcrProviderTest {
    @Test
    void sendsDedicatedPromptImageWordBoxesAndSimpleJsonSchema() throws Exception {
        AiProperties shared = new AiProperties();
        shared.getGemini().setApiKey("test-key");
        shared.getGemini().setBaseUrl("https://gemini.test/models");
        ProductNutritionOcrProperties properties = new ProductNutritionOcrProperties();
        properties.setGeminiModel("product-ocr-model");
        RestOperations http = mock(RestOperations.class);
        when(http.postForObject(anyString(), any(HttpEntity.class), eq(String.class))).thenReturn("""
                {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{\\"fields\\":[{\\"name\\":\\"energy\\",\\"rawValue\\":\\"200\\",\\"unit\\":\\"kcal\\",\\"basis\\":\\"PER_100G\\",\\"evidenceBox\\":{\\"x\\":0.7,\\"y\\":0.2,\\"width\\":0.1,\\"height\\":0.03}}]}"}]}}]}
                """);
        GeminiProductNutritionOcrProvider provider = new GeminiProductNutritionOcrProvider(
                shared, properties, http, new ObjectMapper(),
                new ProductNutritionOcrImagePreprocessor(properties), new ProductNutritionOcrResultValidator());
        var request = new ProductNutritionOcrFallbackRequest(1L, 2L, 3L, "nutrition-label-v4", 0.5,
                List.of("energy"), List.of(Map.of("text", "200", "box", Map.of("x", 0.7))),
                true, "product-nutrition-ai-v1");

        var result = provider.analyze(request, new ProductNutritionOcrEvidence(image(), "image/png", "checksum"));

        assertEquals("product-ocr-model", result.model());
        assertTrue(result.fields().containsKey("energy"));
        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(http).postForObject(eq("https://gemini.test/models/product-ocr-model:generateContent"),
                entity.capture(), eq(String.class));
        assertEquals("test-key", entity.getValue().getHeaders().getFirst("x-goog-api-key"));
        Map<?, ?> payload = (Map<?, ?>) entity.getValue().getBody();
        Map<?, ?> config = (Map<?, ?>) payload.get("generationConfig");
        assertTrue(config.containsKey("responseJsonSchema"));
        assertFalse(config.containsKey("responseSchema"));
        String serialized = new ObjectMapper().writeValueAsString(payload);
        assertTrue(serialized.contains("Never guess"));
        assertTrue(serialized.contains("uncertain fields"));
        assertTrue(serialized.contains("word boxes"));
        assertTrue(serialized.contains("image/png"));
        assertFalse(serialized.contains("additionalProperties"));
    }

    @Test
    void retriesTransientTransportFailureWithinConfiguredAttemptBudget() throws Exception {
        Fixture fixture = fixture(2);
        when(fixture.http.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(validResponse());

        fixture.provider.analyze(fixture.request, fixture.evidence);

        verify(fixture.http, times(2)).postForObject(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void returnsControlledProviderErrorForSchemaMismatchWithoutRetry() throws Exception {
        Fixture fixture = fixture(2);
        when(fixture.http.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{\\\"wrong\\\":[]}\"}]}}]}");

        assertThrows(AiProviderException.class,
                () -> fixture.provider.analyze(fixture.request, fixture.evidence));
        verify(fixture.http, times(1)).postForObject(anyString(), any(HttpEntity.class), eq(String.class));
    }

    private Fixture fixture(int maxAttempts) throws Exception {
        AiProperties shared = new AiProperties();
        shared.getGemini().setApiKey("test-key");
        shared.getGemini().setBaseUrl("https://gemini.test/models");
        ProductNutritionOcrProperties properties = new ProductNutritionOcrProperties();
        properties.setGeminiModel("product-ocr-model");
        properties.setMaxAttempts(maxAttempts);
        RestOperations http = mock(RestOperations.class);
        GeminiProductNutritionOcrProvider provider = new GeminiProductNutritionOcrProvider(
                shared, properties, http, new ObjectMapper(),
                new ProductNutritionOcrImagePreprocessor(properties), new ProductNutritionOcrResultValidator());
        ProductNutritionOcrFallbackRequest request = new ProductNutritionOcrFallbackRequest(
                1L, 2L, 3L, "nutrition-label-v4", 0.5, List.of("energy"), List.of(),
                true, "product-nutrition-ai-v1");
        return new Fixture(http, provider, request,
                new ProductNutritionOcrEvidence(image(), "image/png", "checksum"));
    }

    private String validResponse() {
        return "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{\\\"fields\\\":[{\\\"name\\\":\\\"energy\\\",\\\"rawValue\\\":\\\"200\\\",\\\"unit\\\":\\\"kcal\\\",\\\"basis\\\":\\\"PER_100G\\\",\\\"evidenceBox\\\":{\\\"x\\\":0.7,\\\"y\\\":0.2,\\\"width\\\":0.1,\\\"height\\\":0.03}}]}\"}]}}]}";
    }

    private byte[] image() throws Exception {
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private record Fixture(
            RestOperations http,
            GeminiProductNutritionOcrProvider provider,
            ProductNutritionOcrFallbackRequest request,
            ProductNutritionOcrEvidence evidence) { }
}
