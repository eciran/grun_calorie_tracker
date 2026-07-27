package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ProgressAnalyticsDto;
import com.grun.calorietracker.dto.ProgressBasicAnalyticsDto;
import com.grun.calorietracker.service.ProgressAnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProgressAnalyticsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ProgressAnalyticsService progressAnalyticsService;

    @Test
    @WithMockUser(username = "analytics@grun.app")
    void getAnalytics_ReturnsProAnalyticsContract() throws Exception {
        ProgressAnalyticsDto response = ProgressAnalyticsDto.builder()
                .range(ProgressAnalyticsDto.Range.builder()
                        .startDate(LocalDate.of(2026, 7, 1))
                        .endDate(LocalDate.of(2026, 7, 7))
                        .dayCount(7)
                        .aggregation("DAY")
                        .build())
                .build();
        when(progressAnalyticsService.getAnalytics(
                "analytics@grun.app",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 7),
                true)).thenReturn(response);

        mockMvc.perform(get("/api/v1/progress/analytics")
                        .param("start", "2026-07-01")
                        .param("end", "2026-07-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range.dayCount").value(7))
                .andExpect(jsonPath("$.range.aggregation").value("DAY"));

        verify(progressAnalyticsService).getAnalytics(
                "analytics@grun.app",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 7),
                true);
    }

    @Test
    @WithMockUser(username = "basic@grun.app")
    void getBasicAnalytics_ReturnsPlanIndependentPeriodContract() throws Exception {
        ProgressBasicAnalyticsDto response = ProgressBasicAnalyticsDto.builder()
                .range(ProgressAnalyticsDto.Range.builder()
                        .startDate(LocalDate.of(2026, 7, 1))
                        .endDate(LocalDate.of(2026, 7, 30))
                        .dayCount(30)
                        .aggregation("WEEK")
                        .build())
                .dataCoverage(ProgressAnalyticsDto.DataCoverage.builder()
                        .foodLoggedDays(12)
                        .diaryDays(14)
                        .build())
                .build();
        when(progressAnalyticsService.getBasicAnalytics(
                "basic@grun.app", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 30)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/progress/analytics/basic")
                        .param("start", "2026-07-01")
                        .param("end", "2026-07-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range.dayCount").value(30))
                .andExpect(jsonPath("$.range.aggregation").value("WEEK"))
                .andExpect(jsonPath("$.dataCoverage.foodLoggedDays").value(12))
                .andExpect(jsonPath("$.previousPeriod").doesNotExist())
                .andExpect(jsonPath("$.relationships").doesNotExist());

        verify(progressAnalyticsService).getBasicAnalytics(
                "basic@grun.app", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 30));
    }
    @Test
    void getAnalytics_WithoutAuthentication_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/progress/analytics")
                        .param("start", "2026-07-01")
                        .param("end", "2026-07-07"))
                .andExpect(status().isUnauthorized());
    }
}
