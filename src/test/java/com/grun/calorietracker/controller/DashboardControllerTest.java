package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.DailySummaryDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.service.DashboardService;
import com.grun.calorietracker.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Optional;

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
    @MockBean
    private UserService userService;

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
        summary.setNetCalories(930.5);
        summary.setCalorieProgressPercent(56.27);
        summary.setTotalExerciseMinutes(45);
        summary.setHasActiveGoal(true);
        summary.setOnboardingCompleted(true);
        summary.setHasFoodLogs(false);
        summary.setHasExerciseLogs(false);
        summary.setHasAnyDiaryEntry(false);

        when(dashboardService.getDailySummary("user@example.com", date)).thenReturn(summary);

        mockMvc.perform(get("/api/v1/dashboard/daily-summary")
                        .param("date", "2026-05-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryDate").value("2026-05-15"))
                .andExpect(jsonPath("$.targetCalories").value(2400))
                .andExpect(jsonPath("$.consumedCalories").value(1350.5))
                .andExpect(jsonPath("$.burnedCalories").value(420.0))
                .andExpect(jsonPath("$.remainingCalories").value(1469.5))
                .andExpect(jsonPath("$.netCalories").value(930.5))
                .andExpect(jsonPath("$.calorieProgressPercent").value(56.27))
                .andExpect(jsonPath("$.totalExerciseMinutes").value(45))
                .andExpect(jsonPath("$.hasActiveGoal").value(true))
                .andExpect(jsonPath("$.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.hasFoodLogs").value(false))
                .andExpect(jsonPath("$.hasExerciseLogs").value(false))
                .andExpect(jsonPath("$.hasAnyDiaryEntry").value(false));

        verify(dashboardService).getDailySummary("user@example.com", date);
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void getDailySummary_whenDateMissing_usesUserTimeZoneToday() throws Exception {
        UserEntity user = new UserEntity();
        user.setEmail("user@example.com");
        user.setTimeZone("Pacific/Kiritimati");
        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        LocalDate expectedDate = LocalDate.now(java.time.ZoneId.of("Pacific/Kiritimati"));
        DailySummaryDto summary = new DailySummaryDto();
        summary.setSummaryDate(expectedDate);
        when(dashboardService.getDailySummary("user@example.com", expectedDate)).thenReturn(summary);

        mockMvc.perform(get("/api/v1/dashboard/daily-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryDate").value(expectedDate.toString()));

        verify(dashboardService).getDailySummary("user@example.com", expectedDate);
    }
}

