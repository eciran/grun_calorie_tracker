package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.service.FoodLogsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FoodLogsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FoodLogsService foodLogsService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void testAddFoodLog_success() throws Exception {
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

        when(foodLogsService.addFoodLog(any(FoodLogsDto.class), eq("test@test.com"))).thenReturn(savedDto);

        mockMvc.perform(post("/api/food-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.foodName").value("Egg"));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void testGetFoodLogs_success() throws Exception {
        when(foodLogsService.getFoodLogs(eq("test@test.com"), eq("2024-07-20")))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/food-logs")
                        .param("date", "2024-07-20"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void testDeleteFoodLog_success() throws Exception {
        doNothing().when(foodLogsService).deleteFoodLog(1L, "test@test.com");

        mockMvc.perform(delete("/api/food-logs/1"))
                .andExpect(status().isNoContent());
    }
}