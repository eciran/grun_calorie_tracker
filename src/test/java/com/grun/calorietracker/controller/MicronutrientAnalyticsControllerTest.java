package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.MicronutrientAnalyticsDto;
import com.grun.calorietracker.service.MicronutrientAnalyticsService;
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
class MicronutrientAnalyticsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MicronutrientAnalyticsService micronutrientAnalyticsService;

    @Test
    @WithMockUser(username = "micro@grun.app")
    void getAnalytics_ReturnsMicronutrientContract() throws Exception {
        MicronutrientAnalyticsDto response = MicronutrientAnalyticsDto.builder()
                .range(MicronutrientAnalyticsDto.Range.builder()
                        .startDate(LocalDate.of(2026, 7, 1))
                        .endDate(LocalDate.of(2026, 7, 7))
                        .dayCount(7)
                        .pointGranularity("DAY")
                        .build())
                .nutrients(List.of(MicronutrientAnalyticsDto.NutrientMetric.builder()
                        .code("SODIUM")
                        .unit("MG")
                        .build()))
                .build();
        when(micronutrientAnalyticsService.getAnalytics(
                "micro@grun.app",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 7),
                true
        )).thenReturn(response);

        mockMvc.perform(get("/api/v1/progress/micronutrients")
                        .param("start", "2026-07-01")
                        .param("end", "2026-07-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range.dayCount").value(7))
                .andExpect(jsonPath("$.range.pointGranularity").value("DAY"))
                .andExpect(jsonPath("$.nutrients[0].code").value("SODIUM"));

        verify(micronutrientAnalyticsService).getAnalytics(
                "micro@grun.app",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 7),
                true
        );
    }

    @Test
    void getAnalytics_WithoutAuthentication_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/progress/micronutrients")
                        .param("start", "2026-07-01")
                        .param("end", "2026-07-07"))
                .andExpect(status().isUnauthorized());
    }
}
