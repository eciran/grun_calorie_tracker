package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.StepDailySummaryDto;
import com.grun.calorietracker.dto.StepGoalDto;
import com.grun.calorietracker.dto.StepGoalRequestDto;
import com.grun.calorietracker.dto.StepManualLogRequestDto;
import com.grun.calorietracker.dto.StepManualLogResponseDto;
import com.grun.calorietracker.dto.StepRangeSummaryDto;
import com.grun.calorietracker.service.StepTrackingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StepTrackingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StepTrackingService stepTrackingService;

    @Test
    @WithMockUser(username = "user@grun.app")
    void getGoal_returnsGoal() throws Exception {
        StepGoalDto goal = new StepGoalDto();
        goal.setTargetSteps(10000);
        goal.setReminderEnabled(true);
        when(stepTrackingService.getGoal("user@grun.app")).thenReturn(goal);

        mockMvc.perform(get("/api/v1/steps/goal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetSteps").value(10000))
                .andExpect(jsonPath("$.reminderEnabled").value(true));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void updateGoal_returnsUpdatedGoal() throws Exception {
        StepGoalRequestDto request = new StepGoalRequestDto();
        request.setTargetSteps(12000);
        request.setReminderEnabled(true);
        StepGoalDto response = new StepGoalDto();
        response.setTargetSteps(12000);
        response.setReminderEnabled(true);
        when(stepTrackingService.updateGoal(any(), any(StepGoalRequestDto.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/steps/goal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetSteps").value(12000));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void getDailySummary_returnsStepProgress() throws Exception {
        StepDailySummaryDto summary = new StepDailySummaryDto();
        summary.setDate(LocalDate.of(2026, 6, 12));
        summary.setTotalSteps(8500);
        summary.setTargetSteps(10000);
        summary.setProgressPercent(85.0);
        when(stepTrackingService.getDailySummary("user@grun.app", LocalDate.of(2026, 6, 12))).thenReturn(summary);

        mockMvc.perform(get("/api/v1/steps/daily-summary").param("date", "2026-06-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSteps").value(8500))
                .andExpect(jsonPath("$.progressPercent").value(85.0));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void getRangeSummary_returnsTrends() throws Exception {
        StepRangeSummaryDto response = new StepRangeSummaryDto();
        response.setStartDate(LocalDate.of(2026, 6, 1));
        response.setEndDate(LocalDate.of(2026, 6, 7));
        response.setTotalSteps(42000);
        response.setDays(List.of(new StepDailySummaryDto()));
        when(stepTrackingService.getRangeSummary("user@grun.app", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/steps/range-summary")
                        .param("startDate", "2026-06-01")
                        .param("endDate", "2026-06-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSteps").value(42000));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void getManualLogs_returnsOwnedManualMetrics() throws Exception {
        when(stepTrackingService.getManualLogs("user@grun.app", LocalDate.of(2026, 6, 12), LocalDate.of(2026, 6, 12)))
                .thenReturn(List.of(new StepManualLogResponseDto(55L, 1200, 850.0, 45.0, LocalDateTime.of(2026, 6, 12, 18, 30))));

        mockMvc.perform(get("/api/v1/steps/manual-logs")
                        .param("startDate", "2026-06-12")
                        .param("endDate", "2026-06-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(55L))
                .andExpect(jsonPath("$[0].steps").value(1200))
                .andExpect(jsonPath("$[0].caloriesBurned").value(45.0));
    }
    @Test
    @WithMockUser(username = "user@grun.app")
    void addManualLog_returnsCreatedMetric() throws Exception {
        StepManualLogRequestDto request = new StepManualLogRequestDto();
        request.setSteps(1200);
        request.setRecordedAt(LocalDateTime.of(2026, 6, 12, 18, 30));
        when(stepTrackingService.addManualLog(any(), any(StepManualLogRequestDto.class)))
                .thenReturn(new StepManualLogResponseDto(55L, 1200));

        mockMvc.perform(post("/api/v1/steps/manual-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(55L))
                .andExpect(jsonPath("$.steps").value(1200));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void updateManualLog_returnsUpdatedMetric() throws Exception {
        StepManualLogRequestDto request = new StepManualLogRequestDto();
        request.setSteps(1500);
        request.setRecordedAt(LocalDateTime.of(2026, 6, 12, 19, 0));
        when(stepTrackingService.updateManualLog(any(), any(), any(StepManualLogRequestDto.class)))
                .thenReturn(new StepManualLogResponseDto(55L, 1500));

        mockMvc.perform(put("/api/v1/steps/manual-logs/55")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(55L))
                .andExpect(jsonPath("$.steps").value(1500));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void deleteManualLog_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/steps/manual-logs/55"))
                .andExpect(status().isNoContent());

        verify(stepTrackingService).deleteManualLog("user@grun.app", 55L);
    }
}