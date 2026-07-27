package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminUserAnalyticsDayDto;
import com.grun.calorietracker.dto.AdminUserAnalyticsDto;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.service.AdminUserAnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUserAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserAnalyticsService analyticsService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAnalytics_whenAdmin_returnsAggregateDataWithoutUserProfiles() throws Exception {
        LocalDate date = LocalDate.of(2026, 7, 26);
        when(analyticsService.getAnalytics(date, date, "Europe/Dublin"))
                .thenReturn(new AdminUserAnalyticsDto(
                        date,
                        date,
                        "Europe/Dublin",
                        "DAILY",
                        Instant.parse("2026-07-26T10:00:00Z"),
                        20,
                        5,
                        2,
                        8,
                        8,
                        12,
                        18,
                        Map.of(
                                SubscriptionPlan.FREE, 10L,
                                SubscriptionPlan.PLUS, 6L,
                                SubscriptionPlan.PRO, 4L
                        ),
                        List.of(new AdminUserAnalyticsDayDto(date, 2, 8))
                ));

        mockMvc.perform(get("/api/v1/admin/users/analytics")
                        .param("from", "2026-07-26")
                        .param("to", "2026-07-26"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyActiveUsers").value(8))
                .andExpect(jsonPath("$.weeklyActiveUsers").value(12))
                .andExpect(jsonPath("$.planDistribution.PRO").value(4))
                .andExpect(jsonPath("$.daily[0].registrations").value(2))
                .andExpect(jsonPath("$.daily[0].activeUsers").value(8))
                .andExpect(jsonPath("$.users").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getAnalytics_whenStandardUser_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/analytics")
                        .param("from", "2026-07-01")
                        .param("to", "2026-07-26"))
                .andExpect(status().isForbidden());
    }
}
