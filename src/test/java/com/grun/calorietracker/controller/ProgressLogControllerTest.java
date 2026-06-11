package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.ProgressLogDto;
import com.grun.calorietracker.service.ProgressLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProgressLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProgressLogService progressLogService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "testuser@example.com")
    void testAddProgressLog_Success() throws Exception {
        ProgressLogDto request = progressRequest();
        when(progressLogService.saveLog(any(ProgressLogDto.class), eq("testuser@example.com"))).thenReturn(request);

        mockMvc.perform(post("/api/v1/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void testAddProgressLog_Unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(progressRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser@example.com")
    void testAddProgressLog_InvalidPayload_UsesTurkishValidationMessage() throws Exception {
        ProgressLogDto invalidRequest = new ProgressLogDto();

        mockMvc.perform(post("/api/v1/progress")
                        .header("Accept-Language", "tr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Dogrulama hatasi"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Kilo zorunludur")));
    }

    @Test
    @WithMockUser(username = "testuser@example.com")
    void testGetProgress_Success() throws Exception {
        when(progressLogService.getUserLogs("testuser@example.com")).thenReturn(List.of(progressRequest()));

        mockMvc.perform(get("/api/v1/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].weight").value(82.5));
    }

    @Test
    void testGetProgress_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/progress"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser@example.com")
    void listProgressLogs_WithDateRange_UsesInclusiveHistoryRange() throws Exception {
        when(progressLogService.getUserLogs(eq("testuser@example.com"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(new ProgressLogDto()));

        mockMvc.perform(get("/api/v1/progress")
                        .param("start", "2026-05-01")
                        .param("end", "2026-05-07"))
                .andExpect(status().isOk());

        verify(progressLogService).getUserLogs(
                "testuser@example.com",
                LocalDate.of(2026, 5, 1).atStartOfDay(),
                LocalDate.of(2026, 5, 8).atStartOfDay()
        );
    }

    @Test
    @WithMockUser(username = "testuser@example.com")
    void listProgressLogs_WhenRangeEndMissing_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/progress").param("start", "2026-05-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value("/api/v1/progress"));
    }

    @Test
    @WithMockUser(username = "testuser@example.com")
    void progressCrud_UsesCanonicalProgressPaths() throws Exception {
        ProgressLogDto response = new ProgressLogDto();
        response.setId(5L);
        response.setLogDate(LocalDate.of(2026, 5, 22).atStartOfDay());
        response.setWeight(81.2);
        when(progressLogService.saveLog(any(ProgressLogDto.class), eq("testuser@example.com"))).thenReturn(response);
        when(progressLogService.getLog(5L, "testuser@example.com")).thenReturn(response);
        when(progressLogService.updateLog(eq(5L), any(ProgressLogDto.class), eq("testuser@example.com"))).thenReturn(response);

        ProgressLogDto request = new ProgressLogDto();
        request.setWeight(81.2);

        mockMvc.perform(post("/api/v1/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));

        mockMvc.perform(get("/api/v1/progress/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weight").value(81.2));

        mockMvc.perform(put("/api/v1/progress/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/progress/5"))
                .andExpect(status().isNoContent());

        verify(progressLogService).deleteLog(5L, "testuser@example.com");
    }

    private ProgressLogDto progressRequest() {
        ProgressLogDto request = new ProgressLogDto();
        request.setLogDate(LocalDate.now().atStartOfDay());
        request.setWeight(82.5);
        request.setCalorieIntake(2100);
        request.setProteinIntake(160.0);
        request.setFatIntake(70.0);
        request.setCarbIntake(240.0);
        return request;
    }
}

