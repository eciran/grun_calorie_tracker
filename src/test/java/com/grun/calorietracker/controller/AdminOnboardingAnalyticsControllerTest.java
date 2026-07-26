package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminOnboardingAnalyticsDto;
import com.grun.calorietracker.service.AdminOnboardingAnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminOnboardingAnalyticsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminOnboardingAnalyticsService analyticsService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSummary_whenAdmin_returnsFunnelCounts() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 7, 25, 0, 0);
        when(analyticsService.getSummary(168)).thenReturn(new AdminOnboardingAnalyticsDto(
                168, now.minusHours(168), now, 10, 42, 30, 2, 4, 7, 6, 3
        ));

        mockMvc.perform(get("/api/v1/admin/onboarding/analytics").param("hours", "168"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.started").value(10))
                .andExpect(jsonPath("$.completed").value(6))
                .andExpect(jsonPath("$.abandoned").value(3));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getSummary_whenStandardUser_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/onboarding/analytics"))
                .andExpect(status().isForbidden());
    }
}
