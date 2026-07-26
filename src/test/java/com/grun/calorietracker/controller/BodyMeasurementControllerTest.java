package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.BodyMeasurementDto;
import com.grun.calorietracker.dto.BodyMeasurementRequestDto;
import com.grun.calorietracker.dto.BodyMeasurementSummaryDto;
import com.grun.calorietracker.enums.HealthProvider;
import com.grun.calorietracker.service.BodyMeasurementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BodyMeasurementControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private BodyMeasurementService service;

    @Test
    @WithMockUser(username = "progress@grun.app")
    void create_ReturnsCanonicalMeasurement() throws Exception {
        BodyMeasurementDto response = BodyMeasurementDto.builder()
                .id(9L).recordedAt(LocalDateTime.of(2026, 7, 20, 8, 0))
                .weightKg(80.0).waistCm(90.0).provider(HealthProvider.MANUAL).build();
        when(service.create(any(), eq("progress@grun.app"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/progress/measurements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weightKg").value(80.0))
                .andExpect(jsonPath("$.provider").value("MANUAL"));
    }

    @Test
    @WithMockUser(username = "progress@grun.app")
    void create_AllowsMetricLengthAboveImperialMaximum() throws Exception {
        BodyMeasurementRequestDto request = request();
        request.setWaist(130.0);
        when(service.create(any(), eq("progress@grun.app"))).thenReturn(BodyMeasurementDto.builder()
                .id(10L).recordedAt(request.getRecordedAt()).weightKg(80.0).waistCm(130.0)
                .provider(HealthProvider.MANUAL).build());

        mockMvc.perform(post("/api/v1/progress/measurements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.waistCm").value(130.0));
    }

    @Test
    @WithMockUser(username = "progress@grun.app")
    void create_WithoutValues_ReturnsLocalizedValidationError() throws Exception {
        BodyMeasurementRequestDto request = request();
        request.setWeight(null);

        mockMvc.perform(post("/api/v1/progress/measurements")
                        .header("Accept-Language", "tr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser(username = "progress@grun.app")
    void list_UsesInclusiveDateRange() throws Exception {
        when(service.list(eq("progress@grun.app"), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/progress/measurements")
                        .param("start", "2026-07-01")
                        .param("end", "2026-07-20"))
                .andExpect(status().isOk());

        verify(service).list("progress@grun.app",
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 7, 21, 0, 0));
    }

    @Test
    @WithMockUser(username = "progress@grun.app")
    void summary_ReturnsBasicValues() throws Exception {
        when(service.summary("progress@grun.app")).thenReturn(BodyMeasurementSummaryDto.builder()
                .recordCount(2).currentWeightKg(80.0).previousWeightKg(82.0).weightChangeKg(-2.0).build());

        mockMvc.perform(get("/api/v1/progress/measurements/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordCount").value(2))
                .andExpect(jsonPath("$.weightChangeKg").value(-2.0));
    }

    @Test
    void endpoints_RequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/progress/measurements/summary"))
                .andExpect(status().isUnauthorized());
    }

    private BodyMeasurementRequestDto request() {
        BodyMeasurementRequestDto request = new BodyMeasurementRequestDto();
        request.setRecordedAt(LocalDateTime.of(2026, 7, 20, 8, 0));
        request.setWeight(80.0);
        request.setProvider(HealthProvider.MANUAL);
        return request;
    }
}
