package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.AiRecipeDraftRequestDto;
import com.grun.calorietracker.dto.AiRecipeDraftResponseDto;
import com.grun.calorietracker.dto.AiPhotoMealDraftRequestDto;
import com.grun.calorietracker.dto.AiVoiceFoodDraftRequestDto;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.exception.AiProviderException;
import com.grun.calorietracker.service.impl.OpenAiAiMealDraftProviderClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiAiMealDraftProviderClientTest {

    @Test
    void createVoiceFoodDraft_postsResponsesPayloadAndParsesOutputText() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        OpenAiAiMealDraftProviderClient client = new OpenAiAiMealDraftProviderClient(properties(), restTemplate, new ObjectMapper());

        server.expect(requestTo("https://api.openai.test/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(jsonPath("$.model").value("gpt-5.4-mini"))
                .andExpect(jsonPath("$.store").value(false))
                .andRespond(withSuccess(outputMessageResponse("""
                        {"summary":"Draft created.","items":[{"name":"Chicken and rice","quantity":1,"unit":"serving","estimatedCalories":420,"confidence":0.82}]}
                        """), MediaType.APPLICATION_JSON));

        AiMealDraftResponseDto response = client.createVoiceFoodDraft(voiceRequest());

        assertEquals(AiProvider.OPENAI, client.provider());
        assertEquals("Draft created.", response.getSummary());
        assertEquals("Chicken and rice", response.getItems().get(0).getName());
        server.verify();
    }

    @Test
    void createPhotoMealDraft_whenHttpsImageReference_includesInputImage() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        OpenAiAiMealDraftProviderClient client = new OpenAiAiMealDraftProviderClient(properties(), restTemplate, new ObjectMapper());

        AiPhotoMealDraftRequestDto request = new AiPhotoMealDraftRequestDto();
        request.setImageReference("https://cdn.grun.test/meal.jpg");

        server.expect(requestTo("https://api.openai.test/v1/responses"))
                .andExpect(jsonPath("$.model").value("gpt-5.4-mini"))
                .andRespond(withSuccess(outputTextResponse("""
                        {"summary":"Photo draft.","items":[{"name":"Meal","quantity":1,"unit":"plate","estimatedCalories":500,"confidence":0.6}]}
                        """), MediaType.APPLICATION_JSON));

        AiMealDraftResponseDto response = client.createPhotoMealDraft(request);

        assertEquals("Photo draft.", response.getSummary());
        server.verify();
    }

    @Test
    void createVoiceFoodDraft_whenOpenAiWrapsJsonInCodeFence_parsesDraft() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        OpenAiAiMealDraftProviderClient client = new OpenAiAiMealDraftProviderClient(properties(), restTemplate, new ObjectMapper());

        server.expect(requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withSuccess(outputTextResponse("""
                        ```json
                        {"summary":"Fenced draft.","items":[{"name":"Eggs","quantity":2,"unit":"piece","estimatedCalories":150,"confidence":0.9}]}
                        ```
                        """), MediaType.APPLICATION_JSON));

        AiMealDraftResponseDto response = client.createVoiceFoodDraft(voiceRequest());

        assertEquals("Fenced draft.", response.getSummary());
        assertEquals("Eggs", response.getItems().get(0).getName());
        server.verify();
    }

    @Test
    void createRecipeDraft_whenProviderUsesShortGramUnit_normalizesPortionUnit() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        OpenAiAiMealDraftProviderClient client = new OpenAiAiMealDraftProviderClient(properties(), restTemplate, new ObjectMapper());

        server.expect(requestTo("https://api.openai.test/v1/responses"))
                .andExpect(jsonPath("$.text.format.schema.properties.suggestedIngredients.items.properties.portionUnit.enum[0]").value("GRAM"))
                .andRespond(withSuccess(outputTextResponse("""
                        {
                          "summary":"Recipe draft.",
                          "reviewRequired":true,
                          "suggestedRecipe":{"name":"Chicken rice bowl","mealType":"DINNER","totalYieldGrams":500,"defaultServingGrams":250,"servingCount":2},
                          "suggestedIngredients":[{"name":"Chicken breast","portionSize":150,"portionUnit":"g","confidence":0.8}],
                          "warnings":[]
                        }
                        """), MediaType.APPLICATION_JSON));

        AiRecipeDraftResponseDto response = client.createRecipeDraft(recipeRequest());

        assertEquals(FoodPortionUnit.GRAM, response.getSuggestedIngredients().get(0).getPortionUnit());
        server.verify();
    }

    @Test
    void createVoiceFoodDraft_whenOpenAiReturnsNonJsonOutput_throwsProviderException() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        OpenAiAiMealDraftProviderClient client = new OpenAiAiMealDraftProviderClient(properties(), restTemplate, new ObjectMapper());

        server.expect(requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withSuccess(outputTextResponse("I cannot create that as JSON."), MediaType.APPLICATION_JSON));

        AiProviderException ex = assertThrows(AiProviderException.class, () -> client.createVoiceFoodDraft(voiceRequest()));

        assertTrue(ex.getMessage().startsWith("OpenAI provider returned an invalid JSON response: Unrecognized token"));
        server.verify();
    }
    @Test
    void createVoiceFoodDraft_whenProviderFails_throwsSanitizedError() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        OpenAiAiMealDraftProviderClient client = new OpenAiAiMealDraftProviderClient(properties(), restTemplate, new ObjectMapper());

        server.expect(requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"type":"invalid_request_error","code":"invalid_model","message":"The requested model is invalid."}}
                                """));

        AiProviderException ex = assertThrows(AiProviderException.class, () -> client.createVoiceFoodDraft(voiceRequest()));
        assertEquals("OpenAI provider request failed: HTTP 400 - invalid_request_error/invalid_model: The requested model is invalid.", ex.getMessage());
        server.verify();
    }

    private AiProperties properties() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider(AiProvider.OPENAI);
        properties.setModel("gpt-5.4-mini");
        properties.getOpenai().setApiKey("sk-test");
        properties.getOpenai().setBaseUrl("https://api.openai.test/v1/responses");
        properties.getOpenai().setTimeout(Duration.ofSeconds(20));
        return properties;
    }

    private AiVoiceFoodDraftRequestDto voiceRequest() {
        AiVoiceFoodDraftRequestDto request = new AiVoiceFoodDraftRequestDto();
        request.setTranscript("I ate chicken and rice");
        return request;
    }


    private AiRecipeDraftRequestDto recipeRequest() {
        AiRecipeDraftRequestDto request = new AiRecipeDraftRequestDto();
        request.setPrompt("High protein dinner");
        request.setMealType("DINNER");
        request.setServingCount(2);
        return request;
    }
    private String outputMessageResponse(String outputText) {
        return """
                {"output":[{"type":"message","content":[{"type":"output_text","text":%s}]}]}
                """.formatted(toJsonString(outputText.trim()));
    }

    private String outputTextResponse(String outputText) {
        return """
                {"output_text":%s}
                """.formatted(toJsonString(outputText.trim()));
    }

    private String toJsonString(String value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }}