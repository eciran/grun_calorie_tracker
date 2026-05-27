package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.HealthConnectionDto;
import com.grun.calorietracker.dto.HealthConnectionRequestDto;
import com.grun.calorietracker.dto.HealthDataDeleteResponseDto;
import com.grun.calorietracker.dto.HealthDailySummaryDto;
import com.grun.calorietracker.dto.HealthMetricBatchSyncRequestDto;
import com.grun.calorietracker.dto.HealthMetricBatchSyncResponseDto;
import com.grun.calorietracker.dto.HealthMetricSyncRequestDto;
import com.grun.calorietracker.dto.HealthMetricSyncResponseDto;
import com.grun.calorietracker.dto.HealthRangeSummaryDto;
import com.grun.calorietracker.enums.HealthConnectionStatus;
import com.grun.calorietracker.enums.HealthProvider;
import com.grun.calorietracker.service.HealthIntegrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HealthIntegrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HealthIntegrationService healthIntegrationService;

    @Test
    @WithMockUser(username = "user@example.com")
    void getConnections_returnsCurrentUserConnections() throws Exception {
        HealthConnectionDto connection = new HealthConnectionDto();
        connection.setId(1L);
        connection.setProvider(HealthProvider.APPLE_HEALTH);
        connection.setStatus(HealthConnectionStatus.CONNECTED);
        when(healthIntegrationService.getConnections("user@example.com")).thenReturn(List.of(connection));

        mockMvc.perform(get("/api/v1/health/connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].provider").value("APPLE_HEALTH"))
                .andExpect(jsonPath("$[0].status").value("CONNECTED"));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void connect_marksProviderConnected() throws Exception {
        HealthConnectionRequestDto request = new HealthConnectionRequestDto();
        request.setDeviceModel("iPhone 15");

        HealthConnectionDto response = new HealthConnectionDto();
        response.setProvider(HealthProvider.APPLE_HEALTH);
        response.setStatus(HealthConnectionStatus.CONNECTED);
        when(healthIntegrationService.connect(eq("user@example.com"), eq(HealthProvider.APPLE_HEALTH), any()))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/health/connections/APPLE_HEALTH")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("APPLE_HEALTH"))
                .andExpect(jsonPath("$.status").value("CONNECTED"));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void getDailySummary_returnsAggregatedHealthData() throws Exception {
        HealthDailySummaryDto response = new HealthDailySummaryDto();
        response.setSummaryDate(java.time.LocalDate.of(2026, 5, 26));
        response.setConnectedProviders(List.of(HealthProvider.APPLE_HEALTH));
        response.setTotalSteps(8500);
        response.setHasHealthData(true);

        when(healthIntegrationService.getDailySummary(eq("user@example.com"), eq(java.time.LocalDate.of(2026, 5, 26))))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/health/summary")
                        .param("date", "2026-05-26"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryDate").value("2026-05-26"))
                .andExpect(jsonPath("$.connectedProviders[0]").value("APPLE_HEALTH"))
                .andExpect(jsonPath("$.totalSteps").value(8500))
                .andExpect(jsonPath("$.hasHealthData").value(true));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void getRangeSummary_returnsAggregatedHealthDataForRange() throws Exception {
        HealthRangeSummaryDto response = new HealthRangeSummaryDto();
        response.setStartDate(java.time.LocalDate.of(2026, 5, 20));
        response.setEndDate(java.time.LocalDate.of(2026, 5, 26));
        response.setTotalSteps(42000);
        response.setHasHealthData(true);
        response.setDays(List.of());

        when(healthIntegrationService.getRangeSummary(
                eq("user@example.com"),
                eq(java.time.LocalDate.of(2026, 5, 20)),
                eq(java.time.LocalDate.of(2026, 5, 26))
        )).thenReturn(response);

        mockMvc.perform(get("/api/v1/health/summary/range")
                        .param("startDate", "2026-05-20")
                        .param("endDate", "2026-05-26"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-05-20"))
                .andExpect(jsonPath("$.endDate").value("2026-05-26"))
                .andExpect(jsonPath("$.totalSteps").value(42000))
                .andExpect(jsonPath("$.hasHealthData").value(true));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void syncMetric_acceptsNormalizedMobilePayload() throws Exception {
        HealthMetricSyncRequestDto request = new HealthMetricSyncRequestDto();
        request.setExternalId("steps-1");
        request.setSteps(8400);
        request.setRecordedAt(LocalDateTime.of(2026, 5, 26, 8, 30));

        when(healthIntegrationService.syncMetric(eq("user@example.com"), eq(HealthProvider.HEALTH_CONNECT), any()))
                .thenReturn(new HealthMetricSyncResponseDto(5L, HealthProvider.HEALTH_CONNECT, true));

        mockMvc.perform(post("/api/v1/health/HEALTH_CONNECT/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5L))
                .andExpect(jsonPath("$.provider").value("HEALTH_CONNECT"))
                .andExpect(jsonPath("$.inserted").value(true));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void syncMetrics_acceptsBatchPayload() throws Exception {
        HealthMetricSyncRequestDto metric = new HealthMetricSyncRequestDto();
        metric.setExternalId("steps-1");
        metric.setSteps(8400);
        metric.setRecordedAt(LocalDateTime.of(2026, 5, 26, 8, 30));
        HealthMetricBatchSyncRequestDto request = new HealthMetricBatchSyncRequestDto();
        request.setMetrics(List.of(metric));

        when(healthIntegrationService.syncMetrics(eq("user@example.com"), eq(HealthProvider.HEALTH_CONNECT), any()))
                .thenReturn(new HealthMetricBatchSyncResponseDto(
                        HealthProvider.HEALTH_CONNECT,
                        1,
                        1,
                        0,
                        LocalDateTime.of(2026, 5, 26, 8, 30),
                        List.of(new HealthMetricSyncResponseDto(5L, HealthProvider.HEALTH_CONNECT, true))
                ));

        mockMvc.perform(post("/api/v1/health/HEALTH_CONNECT/metrics/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("HEALTH_CONNECT"))
                .andExpect(jsonPath("$.acceptedCount").value(1))
                .andExpect(jsonPath("$.insertedCount").value(1))
                .andExpect(jsonPath("$.updatedCount").value(0));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void deleteProviderData_deletesSyncedMetrics() throws Exception {
        when(healthIntegrationService.deleteProviderData("user@example.com", HealthProvider.APPLE_HEALTH))
                .thenReturn(new HealthDataDeleteResponseDto(HealthProvider.APPLE_HEALTH, 12L));

        mockMvc.perform(delete("/api/v1/health/APPLE_HEALTH/data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("APPLE_HEALTH"))
                .andExpect(jsonPath("$.deletedMetricCount").value(12));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void deleteAllHealthData_deletesAllSyncedMetrics() throws Exception {
        when(healthIntegrationService.deleteAllHealthData("user@example.com"))
                .thenReturn(new HealthDataDeleteResponseDto(null, 25L));

        mockMvc.perform(delete("/api/v1/health/data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedMetricCount").value(25));
    }
}
