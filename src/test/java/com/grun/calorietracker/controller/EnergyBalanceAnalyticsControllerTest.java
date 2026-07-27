package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.EnergyBalanceAnalyticsDto;
import com.grun.calorietracker.enums.EnergyBalanceState;
import com.grun.calorietracker.service.EnergyBalanceAnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EnergyBalanceAnalyticsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private EnergyBalanceAnalyticsService energyBalanceAnalyticsService;

    @Test
    @WithMockUser(username = "pro@grun.app")
    void getAnalytics_returnsEnergyBalanceContract() throws Exception {
        EnergyBalanceAnalyticsDto response = EnergyBalanceAnalyticsDto.builder()
                .range(EnergyBalanceAnalyticsDto.Range.builder()
                        .startDate(LocalDate.of(2026, 7, 1))
                        .endDate(LocalDate.of(2026, 7, 7))
                        .dayCount(7)
                        .aggregation("DAY")
                        .timeZone("Europe/Dublin")
                        .build())
                .summary(EnergyBalanceAnalyticsDto.Summary.builder()
                        .balanceState(EnergyBalanceState.DEFICIT)
                        .evaluatedDays(7)
                        .build())
                .dailyPoints(List.of(EnergyBalanceAnalyticsDto.DailyPoint.builder()
                        .date(LocalDate.of(2026, 7, 1))
                        .energyBalanceCalories(-400.0)
                        .build()))
                .build();
        when(energyBalanceAnalyticsService.getAnalytics(
                "pro@grun.app", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/progress/energy-balance")
                        .param("start", "2026-07-01")
                        .param("end", "2026-07-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range.dayCount").value(7))
                .andExpect(jsonPath("$.range.aggregation").value("DAY"))
                .andExpect(jsonPath("$.range.timeZone").value("Europe/Dublin"))
                .andExpect(jsonPath("$.summary.balanceState").value("DEFICIT"))
                .andExpect(jsonPath("$.dailyPoints[0].energyBalanceCalories").value(-400.0));

        verify(energyBalanceAnalyticsService).getAnalytics(
                "pro@grun.app", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7));
    }

    @Test
    void getAnalytics_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/progress/energy-balance")
                        .param("start", "2026-07-01")
                        .param("end", "2026-07-07"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "pro@grun.app")
    void getAnalytics_withMissingDate_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/progress/energy-balance")
                        .param("start", "2026-07-01"))
                .andExpect(status().isBadRequest());
    }
}