package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.WaterDailySummaryDto;
import com.grun.calorietracker.dto.WaterDailyTrendDto;
import com.grun.calorietracker.dto.WaterGoalDto;
import com.grun.calorietracker.dto.WaterGoalRequestDto;
import com.grun.calorietracker.dto.WaterLogDto;
import com.grun.calorietracker.dto.WaterLogRequestDto;
import com.grun.calorietracker.dto.WaterRangeSummaryDto;
import com.grun.calorietracker.dto.WaterReminderSettingsDto;
import com.grun.calorietracker.dto.WaterReminderSettingsRequestDto;
import com.grun.calorietracker.service.WaterTrackingService;
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
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WaterTrackingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WaterTrackingService waterTrackingService;

    @Test
    @WithMockUser(username = "user@grun.app")
    void addWaterLog_returnsCreatedLog() throws Exception {
        WaterLogRequestDto request = new WaterLogRequestDto();
        request.setLogDate(LocalDate.of(2026, 6, 5));
        request.setAmountMl(250);
        request.setSource("MANUAL");
        request.setLoggedAt(LocalDateTime.of(2026, 6, 5, 10, 15));

        WaterLogDto response = new WaterLogDto();
        response.setId(1L);
        response.setLogDate(request.getLogDate());
        response.setAmountMl(250);
        response.setSource("MANUAL");
        response.setLoggedAt(request.getLoggedAt());

        when(waterTrackingService.addWaterLog(any(), any(WaterLogRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/water-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.amountMl").value(250));
    }


    @Test
    @WithMockUser(username = "user@grun.app")
    void updateWaterLog_returnsUpdatedLog() throws Exception {
        WaterLogRequestDto request = new WaterLogRequestDto();
        request.setLogDate(LocalDate.of(2026, 6, 5));
        request.setAmountMl(400);
        request.setSource("MANUAL");
        request.setLoggedAt(LocalDateTime.of(2026, 6, 5, 11, 30));

        WaterLogDto response = new WaterLogDto();
        response.setId(1L);
        response.setLogDate(request.getLogDate());
        response.setAmountMl(400);
        response.setSource("MANUAL");
        response.setLoggedAt(request.getLoggedAt());

        when(waterTrackingService.updateWaterLog(any(), any(), any(WaterLogRequestDto.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/water-logs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.amountMl").value(400));
    }
    @Test
    @WithMockUser(username = "user@grun.app")
    void addWaterLog_whenAmountMissing_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/water-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "logDate": "2026-06-05"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation error"));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void getDailySummary_returnsHydrationTotals() throws Exception {
        WaterDailySummaryDto response = new WaterDailySummaryDto();
        response.setDate(LocalDate.of(2026, 6, 5));
        response.setTotalMl(1000);
        response.setTargetMl(2500);
        response.setRemainingMl(1500);
        response.setProgressPercent(40.0);
        response.setLogs(List.of());

        when(waterTrackingService.getDailySummary("user@grun.app", LocalDate.of(2026, 6, 5)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/water-logs/daily-summary")
                        .param("date", "2026-06-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMl").value(1000))
                .andExpect(jsonPath("$.targetMl").value(2500))
                .andExpect(jsonPath("$.remainingMl").value(1500));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void getRangeSummary_returnsHydrationTrend() throws Exception {
        WaterDailyTrendDto day = new WaterDailyTrendDto();
        day.setDate(LocalDate.of(2026, 6, 5));
        day.setTotalMl(1500);
        day.setTargetMl(2500);
        day.setRemainingMl(1000);
        day.setProgressPercent(60.0);
        day.setTargetReached(false);

        WaterRangeSummaryDto response = new WaterRangeSummaryDto();
        response.setStartDate(LocalDate.of(2026, 6, 5));
        response.setEndDate(LocalDate.of(2026, 6, 5));
        response.setTargetMl(2500);
        response.setTotalMl(1500);
        response.setAverageMl(1500.0);
        response.setBestMl(1500);
        response.setTargetHitDays(0);
        response.setDayCount(1);
        response.setDays(List.of(day));

        when(waterTrackingService.getRangeSummary("user@grun.app", LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 5)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/water-logs/range-summary")
                        .param("startDate", "2026-06-05")
                        .param("endDate", "2026-06-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMl").value(1500))
                .andExpect(jsonPath("$.days[0].progressPercent").value(60.0));
    }
    @Test
    @WithMockUser(username = "user@grun.app")
    void getWaterGoal_returnsGoal() throws Exception {
        WaterGoalDto response = new WaterGoalDto();
        response.setTargetMl(2800);
        when(waterTrackingService.getGoal("user@grun.app")).thenReturn(response);

        mockMvc.perform(get("/api/v1/water-logs/goal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetMl").value(2800));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void updateWaterGoal_returnsUpdatedGoal() throws Exception {
        WaterGoalRequestDto request = new WaterGoalRequestDto();
        request.setTargetMl(3000);
        WaterGoalDto response = new WaterGoalDto();
        response.setTargetMl(3000);

        when(waterTrackingService.updateGoal(any(), any(WaterGoalRequestDto.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/water-logs/goal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetMl").value(3000));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void deleteWaterLog_returnsNoContent() throws Exception {
        doNothing().when(waterTrackingService).deleteWaterLog("user@grun.app", 1L);

        mockMvc.perform(delete("/api/v1/water-logs/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void getReminderSettings_returnsSettings() throws Exception {
        WaterReminderSettingsDto response = reminderSettingsResponse();
        when(waterTrackingService.getReminderSettings("user@grun.app")).thenReturn(response);

        mockMvc.perform(get("/api/v1/water-logs/reminder-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.intervalMinutes").value(120));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void updateReminderSettings_returnsUpdatedSettings() throws Exception {
        WaterReminderSettingsRequestDto request = new WaterReminderSettingsRequestDto();
        request.setEnabled(true);
        request.setIntervalMinutes(120);
        request.setStartTime(LocalTime.of(9, 0));
        request.setEndTime(LocalTime.of(21, 0));
        WaterReminderSettingsDto response = reminderSettingsResponse();

        when(waterTrackingService.updateReminderSettings(any(), any(WaterReminderSettingsRequestDto.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/water-logs/reminder-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.startTime").value("09:00:00"));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void updateReminderSettings_whenIntervalTooSmall_returnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/water-logs/reminder-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "intervalMinutes": 10,
                                  "startTime": "09:00:00",
                                  "endTime": "21:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation error"));
    }

    private WaterReminderSettingsDto reminderSettingsResponse() {
        WaterReminderSettingsDto response = new WaterReminderSettingsDto();
        response.setId(1L);
        response.setEnabled(true);
        response.setIntervalMinutes(120);
        response.setStartTime(LocalTime.of(9, 0));
        response.setEndTime(LocalTime.of(21, 0));
        return response;
    }
}
