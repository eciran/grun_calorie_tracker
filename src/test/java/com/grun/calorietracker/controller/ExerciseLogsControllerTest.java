package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.ExerciseLogsDto;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void getExerciseLogsBySource_success() throws Exception {
        ExerciseLogsDto response = new ExerciseLogsDto();
        response.setId(10L);
        response.setSource("GOOGLE_FIT");
        response.setExternalId("fit-123");

        when(exerciseLogsService.getExerciseLogsBySource("test@test.com", "GOOGLE_FIT"))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/exercise-logs/source/GOOGLE_FIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L))
                .andExpect(jsonPath("$[0].source").value("GOOGLE_FIT"))
                .andExpect(jsonPath("$[0].externalId").value("fit-123"));
    }
}

