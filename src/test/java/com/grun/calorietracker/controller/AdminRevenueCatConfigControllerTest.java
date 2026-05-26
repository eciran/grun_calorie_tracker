package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.RevenueCatConfigStatusDto;
import com.grun.calorietracker.dto.RevenueCatMappingValidationRequestDto;
import com.grun.calorietracker.dto.RevenueCatMappingValidationResponseDto;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.service.RevenueCatConfigurationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminRevenueCatConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RevenueCatConfigurationService revenueCatConfigurationService;

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getConfigStatus_whenAdmin_returnsSafeConfiguration() throws Exception {
        RevenueCatConfigStatusDto dto = new RevenueCatConfigStatusDto();
        dto.setWebhookAuthorizationConfigured(true);
        dto.setStrictProductMapping(true);
        dto.setPlusEntitlements(List.of("plus"));
        dto.setProEntitlements(List.of("pro"));
        dto.setPlusProductIds(List.of("grun_plus_monthly"));
        dto.setProProductIds(List.of("grun_pro_monthly"));
        dto.setAiAddonQuotas(Map.of("grun_ai_15_credits", 15));
        dto.setAiAddonValidityDays(Map.of("grun_ai_15_credits", 30));
        dto.setDefaultAiAddonValidityDays(30);

        when(revenueCatConfigurationService.getConfigStatus()).thenReturn(dto);

        mockMvc.perform(get("/api/v1/admin/revenuecat/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webhookAuthorizationConfigured").value(true))
                .andExpect(jsonPath("$.proProductIds[0]").value("grun_pro_monthly"))
                .andExpect(jsonPath("$.aiAddonQuotas.grun_ai_15_credits").value(15));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void validateMapping_whenAdmin_returnsValidationResult() throws Exception {
        RevenueCatMappingValidationResponseDto response = new RevenueCatMappingValidationResponseDto();
        response.setRecognized(true);
        response.setMappingType("SUBSCRIPTION");
        response.setSubscriptionPlan(SubscriptionPlan.PRO);
        response.setStrictProductMapping(true);
        response.setMessage("Subscription event maps to PRO.");

        when(revenueCatConfigurationService.validateMapping(any())).thenReturn(response);

        RevenueCatMappingValidationRequestDto request = new RevenueCatMappingValidationRequestDto();
        request.setEventType("INITIAL_PURCHASE");
        request.setProductId("grun_pro_monthly");
        request.setEntitlementIds(List.of("pro"));

        mockMvc.perform(post("/api/v1/admin/revenuecat/mapping/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recognized").value(true))
                .andExpect(jsonPath("$.mappingType").value("SUBSCRIPTION"))
                .andExpect(jsonPath("$.subscriptionPlan").value("PRO"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void validateMapping_whenEventTypeMissing_returnsBadRequest() throws Exception {
        RevenueCatMappingValidationRequestDto request = new RevenueCatMappingValidationRequestDto();
        request.setProductId("grun_pro_monthly");

        mockMvc.perform(post("/api/v1/admin/revenuecat/mapping/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void getConfigStatus_whenNotAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/revenuecat/config"))
                .andExpect(status().isForbidden());
    }
}
