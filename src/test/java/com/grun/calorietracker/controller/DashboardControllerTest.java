package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.DailySummaryDto;
import com.grun.calorietracker.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void getDailySummary_usesAuthenticatedUserAndDate() throws Exception {
        LocalDate date = LocalDate.of(2026, 5, 15);
        DailySummaryDto summary = new DailySummaryDto();
        summary.setSummaryDate(date);
        summary.setTargetCalories(2400);
        summary.setConsumedCalories(1350.5);
        summary.setBurnedCalories(420.0);
        summary.setRemainingCalories(1469.5);
        summary.setTotalExerciseMinutes(45);

        when(dashboardService.getDailySummary("user@example.com", date)).thenReturn(summary);

        mockMvc.perform(get("/api/dashboard/daily-summary")
                        .param("date", "2026-05-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryDate").value("2026-05-15"))
                .andExpect(jsonPath("$.targetCalories").value(2400))
                .andExpect(jsonPath("$.consumedCalories").value(1350.5))
                .andExpect(jsonPath("$.burnedCalories").value(420.0))
                .andExpect(jsonPath("$.remainingCalories").value(1469.5))
                .andExpect(jsonPath("$.totalExerciseMinutes").value(45));

        verify(dashboardService).getDailySummary("user@example.com", date);
    }
}
