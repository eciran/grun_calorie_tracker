package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.AiNutritionPlanDraftRequestDto;
import com.grun.calorietracker.dto.AiPhotoMealDraftRequestDto;
import com.grun.calorietracker.dto.AiVoiceFoodDraftRequestDto;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.service.impl.GeminiAiMealDraftProviderClient;
import com.grun.calorietracker.service.impl.OpenAiAiMealDraftProviderClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiAiMealDraftProviderClientTest {

    @Test
    void createVoiceFoodDraft_preservesPromptSchemaAndUsageContract() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AiProperties properties = properties();
        ObjectMapper mapper = new ObjectMapper();
        OpenAiAiMealDraftProviderClient contract = new OpenAiAiMealDraftProviderClient(properties, restTemplate, mapper);
        GeminiAiMealDraftProviderClient client = new GeminiAiMealDraftProviderClient(
                properties, restTemplate, mapper, contract);

        server.expect(requestTo("https://generativelanguage.googleapis.test/v1beta/models/gemini-3.6-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "gemini-test-key"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text")
                        .value(org.hamcrest.Matchers.containsString("Request type: VOICE_FOOD_LOG")))
                .andExpect(jsonPath("$.contents[0].parts[0].text")
                        .value(org.hamcrest.Matchers.containsString("Voice transcript meal logging request")))
                .andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(12000))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseJsonSchema.properties.items.items.properties.estimatedNutrition.properties.sodium").exists())
                .andExpect(jsonPath("$.generationConfig.responseSchema").doesNotExist())
                .andExpect(jsonPath("$.generationConfig.responseFormat").doesNotExist())
                .andRespond(withSuccess("""
                        {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{\\\"summary\\\":\\\"Draft created.\\\",\\\"items\\\":[]}"}]}}],
                         "usageMetadata":{"promptTokenCount":1200,"candidatesTokenCount":250,"thoughtsTokenCount":50,"totalTokenCount":1500}}
                        """, MediaType.APPLICATION_JSON));

        AiVoiceFoodDraftRequestDto request = new AiVoiceFoodDraftRequestDto();
        request.setTranscript("Chicken and rice");
        AiMealDraftResponseDto response = client.createVoiceFoodDraft(request);

        assertEquals(AiProvider.GEMINI, client.provider());
        assertEquals("Draft created.", response.getSummary());
        assertEquals(1200, response.getPromptTokens());
        assertEquals(300, response.getCompletionTokens());
        assertEquals(1500, response.getTotalTokens());
        assertEquals(0.002025d, response.getEstimatedCost(), 0.0000001d);
        server.verify();
    }

    @Test
    void createPhotoMealDraft_withManagedDataUri_sendsInlineData() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AiProperties properties = properties();
        ObjectMapper mapper = new ObjectMapper();
        OpenAiAiMealDraftProviderClient contract = new OpenAiAiMealDraftProviderClient(properties, restTemplate, mapper);
        GeminiAiMealDraftProviderClient client = new GeminiAiMealDraftProviderClient(
                properties, restTemplate, mapper, contract);

        server.expect(requestTo("https://generativelanguage.googleapis.test/v1beta/models/gemini-3.6-flash:generateContent"))
                .andExpect(jsonPath("$.contents[0].parts[2].inlineData.mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.contents[0].parts[2].inlineData.data").value("AQID"))
                .andRespond(withSuccess("""
                        {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{\\\"summary\\\":\\\"Photo draft.\\\",\\\"items\\\":[]}"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        AiPhotoMealDraftRequestDto request = new AiPhotoMealDraftRequestDto();
        request.setImageReference("data:image/jpeg;base64,AQID");
        AiMealDraftResponseDto response = client.createPhotoMealDraft(request);

        assertEquals("Photo draft.", response.getSummary());
        server.verify();
    }

    @Test
    void createNutritionPlanDraft_usesGeminiCeilingInsteadOfOpenAiAdaptiveBudget() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AiProperties properties = properties();
        ObjectMapper mapper = new ObjectMapper();
        OpenAiAiMealDraftProviderClient contract = new OpenAiAiMealDraftProviderClient(properties, restTemplate, mapper);
        GeminiAiMealDraftProviderClient client = new GeminiAiMealDraftProviderClient(
                properties, restTemplate, mapper, contract);

        server.expect(requestTo("https://generativelanguage.googleapis.test/v1beta/models/gemini-3.6-flash:generateContent"))
                .andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(12000))
                .andRespond(withSuccess("""
                        {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{}"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        AiNutritionPlanDraftRequestDto request = new AiNutritionPlanDraftRequestDto();
        request.setDayCount(1);
        request.setMealsPerDay(4);
        client.createNutritionPlanDraft(request);

        server.verify();
    }

    private AiProperties properties() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider(AiProvider.GEMINI);
        properties.setModel("gemini-3.6-flash");
        properties.getGemini().setApiKey("gemini-test-key");
        properties.getGemini().setBaseUrl("https://generativelanguage.googleapis.test/v1beta/models");
        properties.getGemini().setInputTokenCostPer1m(0.75);
        properties.getGemini().setOutputTokenCostPer1m(3.75);
        return properties;
    }
}
