package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.ExerciseLogsDto;
import com.grun.calorietracker.enums.ExerciseLogMeasurementType;
import com.grun.calorietracker.exception.DuplicateExternalExerciseLogException;
import com.grun.calorietracker.service.ExerciseLogsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExerciseLogsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExerciseLogsService exerciseLogsService;

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void addExternalExerciseLog_success() throws Exception {
        ExerciseLogsDto request = new ExerciseLogsDto();
        request.setExerciseItemId(3L);
        request.setDurationMinutes(45);
        request.setCaloriesBurned(420.0);
        request.setLogDate(LocalDateTime.of(2026, 5, 11, 18, 30));
        request.setSource("APPLE_HEALTH");
        request.setExternalId("workout-123");

        ExerciseLogsDto response = new ExerciseLogsDto();
        response.setId(10L);
        response.setExerciseItemId(3L);
        response.setSource("APPLE_HEALTH");
        response.setExternalId("workout-123");

        when(exerciseLogsService.addExerciseLogFromExternal(any(ExerciseLogsDto.class), eq("test@test.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/exercise-logs/external")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.source").value("APPLE_HEALTH"))
                .andExpect(jsonPath("$.externalId").value("workout-123"));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void addExternalExerciseLog_duplicate_returnsConflict() throws Exception {
        ExerciseLogsDto request = new ExerciseLogsDto();
        request.setExerciseItemId(3L);
        request.setDurationMinutes(45);
        request.setCaloriesBurned(420.0);
        request.setLogDate(LocalDateTime.of(2026, 5, 11, 18, 30));
        request.setSource("APPLE_HEALTH");
        request.setExternalId("workout-123");

        when(exerciseLogsService.addExerciseLogFromExternal(any(ExerciseLogsDto.class), eq("test@test.com")))
                .thenThrow(new DuplicateExternalExerciseLogException("Duplicate"));

        mockMvc.perform(post("/api/v1/exercise-logs/external")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void addExerciseLog_whenRequiredFieldsMissing_returnsBadRequest() throws Exception {
        ExerciseLogsDto request = new ExerciseLogsDto();

        mockMvc.perform(post("/api/v1/exercise-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation error"))
                .andExpect(jsonPath("$.path").value("/api/v1/exercise-logs"));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void addExerciseLog_whenBodyweightRepsProvided_returnsCreatedLog() throws Exception {
        ExerciseLogsDto request = new ExerciseLogsDto();
        request.setExerciseItemId(3L);
        request.setMeasurementType(ExerciseLogMeasurementType.SETS_REPS);
        request.setSetCount(4);
        request.setReps(20);
        request.setCaloriesBurned(80.0);
        request.setLogDate(LocalDateTime.of(2026, 6, 12, 8, 0));

        ExerciseLogsDto response = new ExerciseLogsDto();
        response.setId(22L);
        response.setExerciseItemId(3L);
        response.setMeasurementType(ExerciseLogMeasurementType.SETS_REPS);
        response.setSetCount(4);
        response.setReps(20);
        response.setCaloriesBurned(80.0);
        response.setLogDate(request.getLogDate());

        when(exerciseLogsService.addExerciseLog(any(ExerciseLogsDto.class), eq("test@test.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/exercise-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(22L))
                .andExpect(jsonPath("$.measurementType").value("SETS_REPS"))
                .andExpect(jsonPath("$.setCount").value(4))
                .andExpect(jsonPath("$.reps").value(20));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void getExerciseLogsBySource_success() throws Exception {
        ExerciseLogsDto response = new ExerciseLogsDto();
        response.setId(10L);
        response.setSource("GOOGLE_FIT");
        response.setExternalId("fit-123");

        when(exerciseLogsService.getExerciseLogsBySource("test@test.com", "GOOGLE_FIT", 0, 50))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/exercise-logs/source/GOOGLE_FIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L))
                .andExpect(jsonPath("$[0].source").value("GOOGLE_FIT"))
                .andExpect(jsonPath("$[0].externalId").value("fit-123"));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void getExerciseLogsBySource_withPaginationParams_usesRequestedPage() throws Exception {
        when(exerciseLogsService.getExerciseLogsBySource("test@test.com", "GOOGLE_FIT", 2, 25))
                .thenReturn(List.of(new ExerciseLogsDto()));

        mockMvc.perform(get("/api/v1/exercise-logs/source/GOOGLE_FIT")
                        .param("page", "2")
                        .param("size", "25"))
                .andExpect(status().isOk());

        verify(exerciseLogsService).getExerciseLogsBySource("test@test.com", "GOOGLE_FIT", 2, 25);
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void updateExerciseLog_success() throws Exception {
        ExerciseLogsDto request = new ExerciseLogsDto();
        request.setExerciseItemId(3L);
        request.setDurationMinutes(50);
        request.setCaloriesBurned(450.0);
        request.setLogDate(LocalDateTime.of(2026, 5, 22, 18, 0));
        ExerciseLogsDto response = new ExerciseLogsDto();
        response.setId(9L);
        response.setDurationMinutes(50);
        when(exerciseLogsService.updateExerciseLog(eq(9L), any(ExerciseLogsDto.class), eq("test@test.com")))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/exercise-logs/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9L))
                .andExpect(jsonPath("$.durationMinutes").value(50));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void getExerciseLogHistory_UsesInclusiveDateRange() throws Exception {
        when(exerciseLogsService.getExerciseLogsHistory(eq("test@test.com"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(new ExerciseLogsDto()));

        mockMvc.perform(get("/api/v1/exercise-logs/history")
                        .param("start", "2026-05-01")
                        .param("end", "2026-05-07"))
                .andExpect(status().isOk());

        verify(exerciseLogsService).getExerciseLogsHistory(
                "test@test.com",
                LocalDate.of(2026, 5, 1).atStartOfDay(),
                LocalDate.of(2026, 5, 8).atStartOfDay()
        );
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void getExerciseLogHistory_WhenEndBeforeStart_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/exercise-logs/history")
                        .param("start", "2026-05-07")
                        .param("end", "2026-05-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value("/api/v1/exercise-logs/history"));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void getExerciseLogHistory_WhenRangeTooLarge_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/exercise-logs/history")
                        .param("start", "2025-01-01")
                        .param("end", "2026-06-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Exercise date range must not exceed 366 days."));
    }
}

