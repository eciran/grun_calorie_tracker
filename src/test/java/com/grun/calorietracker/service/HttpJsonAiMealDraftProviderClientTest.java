package com.grun.calorietracker.service;

import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.AiVoiceFoodDraftRequestDto;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.service.impl.HttpJsonAiMealDraftProviderClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.HttpMethod;

class HttpJsonAiMealDraftProviderClientTest {

    @Test
    void createVoiceFoodDraft_postsGenericProviderPayload() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AiProperties properties = properties();
        HttpJsonAiMealDraftProviderClient client = new HttpJsonAiMealDraftProviderClient(properties, restTemplate);

        server.expect(requestTo("https://ai.example.test/meal-drafts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer secret-test-key"))
                .andExpect(header("X-GRun-AI-Provider", "HTTP_JSON"))
                .andExpect(jsonPath("$.requestType").value("VOICE_FOOD_LOG"))
                .andExpect(jsonPath("$.model").value("provider-model-v1"))
                .andExpect(jsonPath("$.input.transcript").value("I ate chicken and rice"))
                .andRespond(withSuccess("""
                        {
                          "requestType": "VOICE_FOOD_LOG",
                          "provider": "HTTP_JSON",
                          "model": "provider-model-v1",
                          "status": "DRAFT_CREATED",
                          "items": [
                            {
                              "name": "Chicken and rice",
                              "quantity": 150,
                              "unit": "g",
                              "estimatedCalories": 350,
                              "confidence": 0.8
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        AiMealDraftResponseDto response = client.createVoiceFoodDraft(request());

        assertEquals(AiRequestType.VOICE_FOOD_LOG, response.getRequestType());
        assertEquals(AiProvider.HTTP_JSON, response.getProvider());
        assertEquals(AiRequestStatus.DRAFT_CREATED, response.getStatus());
        assertEquals("Chicken and rice", response.getItems().get(0).getName());
        server.verify();
    }

    @Test
    void createVoiceFoodDraft_whenProviderFails_throwsSanitizedError() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        HttpJsonAiMealDraftProviderClient client = new HttpJsonAiMealDraftProviderClient(properties(), restTemplate);

        server.expect(requestTo("https://ai.example.test/meal-drafts"))
                .andRespond(withBadRequest());

        assertThrows(IllegalArgumentException.class, () -> client.createVoiceFoodDraft(request()));
        server.verify();
    }

    private AiProperties properties() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider(AiProvider.HTTP_JSON);
        properties.setModel("provider-model-v1");
        properties.getHttpJson().setEndpoint("https://ai.example.test/meal-drafts");
        properties.getHttpJson().setApiKey("secret-test-key");
        properties.getHttpJson().setTimeout(Duration.ofSeconds(10));
        return properties;
    }

    private AiVoiceFoodDraftRequestDto request() {
        AiVoiceFoodDraftRequestDto request = new AiVoiceFoodDraftRequestDto();
        request.setTranscript("I ate chicken and rice");
        return request;
    }
}
