package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminDashboardSummaryDto;
import com.grun.calorietracker.service.AdminDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminDashboardService adminDashboardService;

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void getSummary_whenAdmin_returnsDashboardMetrics() throws Exception {
        AdminDashboardSummaryDto summary = new AdminDashboardSummaryDto();
        summary.setTotalUsers(10);
        summary.setStandardUsers(7);
        summary.setProUsers(2);
        summary.setAdminUsers(1);
        summary.setTotalProducts(100);
        summary.setVerifiedProducts(60);
        summary.setRawImportedProducts(25);
        summary.setNeedsReviewProducts(10);
        summary.setRejectedProducts(5);
        summary.setReviewQueueProducts(35);
        summary.setPendingRecipeApprovals(4);
        summary.setPendingRecipeImportCandidates(2);
        summary.setOpenRecipeReports(3);
        summary.setOpenProductCorrectionSuggestions(5);
        summary.setOpenProductQualitySuggestions(6);
        summary.setRefundableAiRequests(7);
        summary.setTotalAdminApprovalItems(62);
        summary.setActivePlusSubscriptions(4);
        summary.setActiveProSubscriptions(2);
        summary.setCanceledSubscriptions(3);
        summary.setRefundedSubscriptions(1);
        summary.setAiQuotaExhaustedSubscriptions(6);
        summary.setFailedSubscriptionProviderEvents(8);
        summary.setSubscriptionProviderEventsLast24Hours(12);
        summary.setAiRequestsLast7Days(50);
        summary.setAiConfirmedLast7Days(30);
        summary.setAiRejectedLast7Days(12);
        summary.setAiFailedLast7Days(3);
        summary.setAiRejectionReasonsLast7Days(Map.of("WRONG_PORTION", 7L, "LOW_CONFIDENCE", 5L));

        when(adminDashboardService.getSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/v1/admin/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(10))
                .andExpect(jsonPath("$.standardUsers").value(7))
                .andExpect(jsonPath("$.proUsers").value(2))
                .andExpect(jsonPath("$.adminUsers").value(1))
                .andExpect(jsonPath("$.totalProducts").value(100))
                .andExpect(jsonPath("$.verifiedProducts").value(60))
                .andExpect(jsonPath("$.rawImportedProducts").value(25))
                .andExpect(jsonPath("$.needsReviewProducts").value(10))
                .andExpect(jsonPath("$.rejectedProducts").value(5))
                .andExpect(jsonPath("$.reviewQueueProducts").value(35))
                .andExpect(jsonPath("$.pendingRecipeApprovals").value(4))
                .andExpect(jsonPath("$.pendingRecipeImportCandidates").value(2))
                .andExpect(jsonPath("$.openRecipeReports").value(3))
                .andExpect(jsonPath("$.openProductCorrectionSuggestions").value(5))
                .andExpect(jsonPath("$.openProductQualitySuggestions").value(6))
                .andExpect(jsonPath("$.refundableAiRequests").value(7))
                .andExpect(jsonPath("$.totalAdminApprovalItems").value(62))                .andExpect(jsonPath("$.activePlusSubscriptions").value(4))
                .andExpect(jsonPath("$.activeProSubscriptions").value(2))
                .andExpect(jsonPath("$.failedSubscriptionProviderEvents").value(8))
                .andExpect(jsonPath("$.subscriptionProviderEventsLast24Hours").value(12))
                .andExpect(jsonPath("$.aiRequestsLast7Days").value(50))
                .andExpect(jsonPath("$.aiConfirmedLast7Days").value(30))
                .andExpect(jsonPath("$.aiRejectedLast7Days").value(12))
                .andExpect(jsonPath("$.aiFailedLast7Days").value(3))
                .andExpect(jsonPath("$.aiRejectionReasonsLast7Days.WRONG_PORTION").value(7));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void getSummary_whenNotAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/summary"))
                .andExpect(status().isForbidden());
    }
}


