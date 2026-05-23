package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.FoodLogDailyStatsDto;
import com.grun.calorietracker.dto.FoodDiaryNoteDto;
import com.grun.calorietracker.dto.FoodDiaryNoteRequestDto;
import com.grun.calorietracker.dto.FoodLogCopyMealRequestDto;
import com.grun.calorietracker.dto.FoodLogMealSummaryDto;
import com.grun.calorietracker.dto.FoodLogRecentMealDto;
import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.service.FoodDiaryNoteService;
import com.grun.calorietracker.service.FoodLogsService;
import com.grun.calorietracker.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class FoodLogsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FoodLogsService foodLogsService;

    @MockBean
    private FoodDiaryNoteService foodDiaryNoteService;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    private UserEntity user;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(1L);
        user.setEmail("test@test.com");
        userDetails = User.builder()
                .username("test@test.com")
                .password("12345")
                .roles("USER")
                .build();
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void testAddFoodLog_success() throws Exception {
        // Arrange
        FoodLogsDto dto = new FoodLogsDto();
        dto.setFoodItemId(1L);
        dto.setPortionSize(100.0);
        dto.setMealType("breakfast");
        dto.setLogDate(LocalDateTime.now());

        FoodLogsDto savedDto = new FoodLogsDto();
        savedDto.setId(1L);
        savedDto.setFoodItemId(1L);
        savedDto.setFoodName("Egg");
        savedDto.setPortionSize(100.0);
        savedDto.setMealType("breakfast");
        savedDto.setLogDate(dto.getLogDate());

        when(foodLogsService.addFoodLog(any(FoodLogsDto.class), eq(user.getEmail()))).thenReturn(savedDto);

        // Act & Assert
        mockMvc.perform(post("/api/v1/food-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.foodName").value("Egg"));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void testAddFoodLog_invalidCredential() throws Exception {
        FoodLogsDto dto = new FoodLogsDto();
        dto.setFoodItemId(1L);
        dto.setPortionSize(100.0);
        dto.setMealType("breakfast");
        dto.setLogDate(LocalDateTime.now());
        doThrow(new InvalidCredentialsException("Invalid credential"))
                .when(foodLogsService).addFoodLog(any(FoodLogsDto.class), eq(user.getEmail()));

        mockMvc.perform(post("/api/v1/food-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void copyMeal_success() throws Exception {
        FoodLogCopyMealRequestDto request = new FoodLogCopyMealRequestDto();
        request.setSourceDate(java.time.LocalDate.of(2026, 5, 21));
        request.setTargetDate(java.time.LocalDate.of(2026, 5, 22));
        request.setMealType("BREAKFAST");
        FoodLogsDto copied = new FoodLogsDto();
        copied.setId(22L);
        copied.setFoodItemId(1L);
        copied.setPortionSize(100.0);
        copied.setMealType("BREAKFAST");
        copied.setLogDate(LocalDateTime.of(2026, 5, 22, 8, 0));
        when(foodLogsService.copyMeal(eq(user.getEmail()), any(FoodLogCopyMealRequestDto.class)))
                .thenReturn(List.of(copied));

        mockMvc.perform(post("/api/v1/food-logs/copy-meal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(22L))
                .andExpect(jsonPath("$[0].logDate").value("2026-05-22T08:00:00"));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void testAddFoodLog_whenRequiredFieldsMissing_returnsBadRequest() throws Exception {
        FoodLogsDto dto = new FoodLogsDto();

        mockMvc.perform(post("/api/v1/food-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation error"))
                .andExpect(jsonPath("$.path").value("/api/v1/food-logs"));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void testGetFoodLogs_success() throws Exception {
        when(userService.findByEmail(anyString())).thenReturn(java.util.Optional.of(user));
        when(foodLogsService.getFoodLogs(eq(user.getEmail()), anyString()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/food-logs")
                        .param("date", "2024-07-20"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void updateFoodLog_success() throws Exception {
        FoodLogsDto dto = new FoodLogsDto();
        dto.setFoodItemId(1L);
        dto.setPortionSize(1.0);
        dto.setMealType("DINNER");
        dto.setLogDate(LocalDateTime.of(2026, 5, 20, 20, 0));

        FoodLogsDto updated = new FoodLogsDto();
        updated.setId(10L);
        updated.setFoodItemId(1L);
        updated.setFoodName("Egg");
        updated.setPortionSize(1.0);
        updated.setMealType("DINNER");
        updated.setLogDate(dto.getLogDate());

        when(foodLogsService.updateFoodLog(eq(10L), any(FoodLogsDto.class), eq(user.getEmail()))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/food-logs/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.mealType").value("DINNER"));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void getFoodLogHistory_success() throws Exception {
        when(foodLogsService.getFoodLogsHistory(eq(user.getEmail()), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/food-logs/history")
                        .param("start", "2026-05-01")
                        .param("end", "2026-05-07"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void getFoodLogHistory_whenRangeIsInvalid_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/food-logs/history")
                        .param("start", "2026-05-07")
                        .param("end", "2026-05-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void getMealSummaries_success() throws Exception {
        FoodLogMealSummaryDto breakfast = new FoodLogMealSummaryDto();
        breakfast.setMealType("BREAKFAST");
        breakfast.setTotalCalories(320.0);
        breakfast.setLogs(Collections.emptyList());
        when(foodLogsService.getMealSummaries(eq(user.getEmail()), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(breakfast));

        mockMvc.perform(get("/api/v1/food-logs/meals")
                        .param("date", "2026-05-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mealType").value("BREAKFAST"))
                .andExpect(jsonPath("$[0].totalCalories").value(320.0));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void getRecentMeals_success() throws Exception {
        FoodLogRecentMealDto recent = new FoodLogRecentMealDto();
        recent.setSourceDate(java.time.LocalDate.of(2026, 5, 21));
        recent.setMealType("DINNER");
        recent.setLogs(Collections.emptyList());
        when(foodLogsService.getRecentMeals(user.getEmail(), 10)).thenReturn(List.of(recent));

        mockMvc.perform(get("/api/v1/food-logs/recent-meals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceDate").value("2026-05-21"))
                .andExpect(jsonPath("$[0].mealType").value("DINNER"));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void upsertDiaryNote_success() throws Exception {
        FoodDiaryNoteRequestDto request = new FoodDiaryNoteRequestDto();
        request.setNote("More protein tomorrow");
        FoodDiaryNoteDto response = new FoodDiaryNoteDto();
        response.setId(5L);
        response.setDiaryDate(LocalDate.of(2026, 5, 23));
        response.setNote("More protein tomorrow");
        when(foodDiaryNoteService.upsertNote(eq(user.getEmail()), eq(LocalDate.of(2026, 5, 23)), any(FoodDiaryNoteRequestDto.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/food-logs/diary-note")
                        .param("date", "2026-05-23")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5L))
                .andExpect(jsonPath("$.diaryDate").value("2026-05-23"))
                .andExpect(jsonPath("$.note").value("More protein tomorrow"));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void getDiaryNote_success() throws Exception {
        FoodDiaryNoteDto response = new FoodDiaryNoteDto();
        response.setId(5L);
        response.setDiaryDate(LocalDate.of(2026, 5, 23));
        response.setNote("More protein tomorrow");
        when(foodDiaryNoteService.getNote(user.getEmail(), LocalDate.of(2026, 5, 23))).thenReturn(response);

        mockMvc.perform(get("/api/v1/food-logs/diary-note")
                        .param("date", "2026-05-23"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value("More protein tomorrow"));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void deleteDiaryNote_success() throws Exception {
        mockMvc.perform(delete("/api/v1/food-logs/diary-note")
                        .param("date", "2026-05-23"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void testDeleteFoodLog_success() throws Exception {
        when(userService.findByEmail(anyString())).thenReturn(java.util.Optional.of(user));
        Mockito.doNothing().when(foodLogsService).deleteFoodLog(eq(1L), eq(user.getEmail()));

        mockMvc.perform(delete("/api/v1/food-logs/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void testGetDailyStats_success() throws Exception {
        FoodLogDailyStatsDto stats = new FoodLogDailyStatsDto();
        stats.setDate("2026-05-01");
        stats.setTotalCalories(450.5);
        stats.setTotalProtein(30.0);
        stats.setTotalCarbs(55.25);
        stats.setTotalFat(12.75);

        when(userService.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(foodLogsService.getDailyStats(eq(user.getEmail()), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(stats));

        mockMvc.perform(get("/api/v1/food-logs/stats")
                        .param("start", "2026-05-01")
                        .param("end", "2026-05-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-05-01"))
                .andExpect(jsonPath("$[0].totalCalories").value(450.5))
                .andExpect(jsonPath("$[0].totalProtein").value(30.0))
                .andExpect(jsonPath("$[0].totalCarbs").value(55.25))
                .andExpect(jsonPath("$[0].totalFat").value(12.75));
    }
}

