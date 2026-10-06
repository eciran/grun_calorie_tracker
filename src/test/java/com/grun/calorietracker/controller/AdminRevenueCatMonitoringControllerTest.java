package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.RevenueCatCustomerEvidenceDto;
import com.grun.calorietracker.service.RevenueCatMonitoringService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminRevenueCatMonitoringControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean RevenueCatMonitoringService monitoring;
    @MockBean com.grun.calorietracker.repository.SubscriptionVerificationRepository verifications;

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "ADMIN_PERMISSION_FINANCE_READ"})
    void unresolvedVerificationQueueShowsSafeDiagnosticsWithoutAttemptSecrets() throws Exception {
        var state = new com.grun.calorietracker.entity.SubscriptionVerificationEntity();
        state.setUserId(18L); state.setProductId("grun_plus_monthly"); state.setStatus("REQUIRES_REVIEW");
        state.setReasonCode("PROVIDER_NETWORK_ERROR"); state.setAttemptId("private-attempt");
        state.setAllocationReference("private-allocation"); state.setAttempts(8);
        when(verifications.findByStatusIn(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(state)));
        mockMvc.perform(get("/api/v1/admin/revenuecat/monitoring/verification-issues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(18))
                .andExpect(jsonPath("$.content[0].reasonCode").value("PROVIDER_NETWORK_ERROR"))
                .andExpect(jsonPath("$.content[0].attemptId").doesNotExist())
                .andExpect(jsonPath("$.content[0].allocationReference").doesNotExist());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void unresolvedVerificationQueueRejectsNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/revenuecat/monitoring/verification-issues"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unresolvedVerificationQueueRejectsAnonymousAccess() throws Exception {
        mockMvc.perform(get("/api/v1/admin/revenuecat/monitoring/verification-issues"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "ADMIN_PERMISSION_FINANCE_READ"})
    void customerEvidenceIsReadOnlyAndScopedToRequestedInternalUser() throws Exception {
        when(monitoring.getCustomerEvidence(44L)).thenReturn(new RevenueCatCustomerEvidenceDto(
                44L, "sandbox", true, "Evidence fetched", LocalDateTime.now(), null, null, List.of()));

        mockMvc.perform(get("/api/v1/admin/revenuecat/monitoring/customer-evidence").param("userId", "44"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(44))
                .andExpect(jsonPath("$.environment").value("sandbox"))
                .andExpect(jsonPath("$.providerReachable").value(true));

        verify(monitoring).getCustomerEvidence(44L);
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void customerEvidenceRejectsNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/revenuecat/monitoring/customer-evidence").param("userId", "44"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerEvidenceRejectsAnonymousAccess() throws Exception {
        mockMvc.perform(get("/api/v1/admin/revenuecat/monitoring/customer-evidence").param("userId", "44"))
                .andExpect(status().isUnauthorized());
    }
}
