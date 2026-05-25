package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.grun.calorietracker.dto.RevenueCatWebhookResponseDto;
import com.grun.calorietracker.service.RevenueCatWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RevenueCatWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RevenueCatWebhookService revenueCatWebhookService;

    @Test
    void receiveWebhook_returnsProcessingResult() throws Exception {
        when(revenueCatWebhookService.processWebhook(eq("Bearer rc-secret"), any(JsonNode.class)))
                .thenReturn(new RevenueCatWebhookResponseDto(true, false, "evt_1", "PROCESSED", "RevenueCat event processed."));

        mockMvc.perform(post("/api/v1/webhooks/revenuecat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer rc-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"event":{"id":"evt_1","type":"INITIAL_PURCHASE","app_user_id":"user:1"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.providerEventId").value("evt_1"))
                .andExpect(jsonPath("$.status").value("PROCESSED"));
    }
}
