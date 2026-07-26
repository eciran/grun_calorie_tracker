package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.SleepSessionDto;
import com.grun.calorietracker.enums.HealthProvider;
import com.grun.calorietracker.enums.SleepQualityConfidence;
import com.grun.calorietracker.service.SleepTrackingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SleepTrackingControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SleepTrackingService service;

    @Test
    @WithMockUser(username = "sleep@grun.app")
    void createManual_ReturnsCanonicalServerResult() throws Exception {
        when(service.createManual(any(), any())).thenReturn(session());

        mockMvc.perform(post("/api/v1/sleep/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "2026-07-20T22:30:00+01:00",
                                  "endAt": "2026-07-21T06:30:00+01:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMinutes").value(480))
                .andExpect(jsonPath("$.sleepDate").value("2026-07-21"));
    }

    @Test
    @WithMockUser(username = "sleep@grun.app")
    void list_ReturnsSessions() throws Exception {
        when(service.list("sleep@grun.app", LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 21)))
                .thenReturn(List.of(session()));

        mockMvc.perform(get("/api/v1/sleep/sessions")
                        .param("start", "2026-07-20")
                        .param("end", "2026-07-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].provider").value("MANUAL"));
    }

    @Test
    void unauthenticatedRequest_IsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/sleep/goal"))
                .andExpect(status().isUnauthorized());
    }

    private SleepSessionDto session() {
        return SleepSessionDto.builder()
                .id(1L)
                .sleepDate(LocalDate.of(2026, 7, 21))
                .durationMinutes(480)
                .provider(HealthProvider.MANUAL)
                .qualityScore(100)
                .qualityConfidence(SleepQualityConfidence.DURATION_ONLY)
                .stages(List.of())
                .build();
    }
}
