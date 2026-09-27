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
