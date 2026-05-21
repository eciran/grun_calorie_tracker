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
        AdminDashboardSummaryDto summary = new AdminDashboardSummaryDto(
                10,
                7,
                2,
                1,
                100,
                60,
                25,
                10,
                5,
                35
        );

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
                .andExpect(jsonPath("$.reviewQueueProducts").value(35));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void getSummary_whenNotAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/summary"))
                .andExpect(status().isForbidden());
    }
}

