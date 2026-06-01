package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.RetentionPolicyDto;
import com.grun.calorietracker.dto.RetentionPolicyUpdateRequestDto;
import com.grun.calorietracker.enums.RetentionPolicyKey;
import com.grun.calorietracker.service.RetentionPolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminRetentionPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RetentionPolicyService retentionPolicyService;

    @Test
    @WithMockUser(username = "admin@grun.app", roles = "ADMIN")
    void listPolicies_whenAdmin_returnsRetentionRules() throws Exception {
        when(retentionPolicyService.listPolicies()).thenReturn(List.of(policy()));

        mockMvc.perform(get("/api/v1/admin/legal/retention-policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].policyKey").value("PAYMENT_AUDIT_EVENTS"))
                .andExpect(jsonPath("$[0].retentionDays").value(2555));
    }

    @Test
    @WithMockUser(username = "admin@grun.app", roles = "ADMIN")
    void upsertPolicy_whenAdmin_updatesPolicy() throws Exception {
        RetentionPolicyUpdateRequestDto request = new RetentionPolicyUpdateRequestDto(
                2555,
                "Payment reconciliation",
                "Keep anonymized payment audit events.",
                true
        );
        when(retentionPolicyService.upsertPolicy(
                eq("admin@grun.app"),
                eq(RetentionPolicyKey.PAYMENT_AUDIT_EVENTS),
                eq(request)
        )).thenReturn(policy());

        mockMvc.perform(put("/api/v1/admin/legal/retention-policies/PAYMENT_AUDIT_EVENTS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyKey").value("PAYMENT_AUDIT_EVENTS"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @WithMockUser(username = "user@grun.app", roles = "USER")
    void listPolicies_whenNotAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/legal/retention-policies"))
                .andExpect(status().isForbidden());
    }

    private RetentionPolicyDto policy() {
        return new RetentionPolicyDto(
                1L,
                RetentionPolicyKey.PAYMENT_AUDIT_EVENTS,
                2555,
                "Payment reconciliation",
                "Keep anonymized payment audit events.",
                true,
                "admin@grun.app",
                LocalDateTime.of(2026, 5, 31, 13, 0)
        );
    }
}
