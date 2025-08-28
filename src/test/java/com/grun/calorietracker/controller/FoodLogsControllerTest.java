package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
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
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
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

        when(userService.findByEmail(anyString())).thenReturn(java.util.Optional.of(user));
        when(foodLogsService.addFoodLog(any(FoodLogsDto.class), eq(user.getName()))).thenReturn(savedDto);

        // Act & Assert
        mockMvc.perform(post("/api/food-logs")
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
        when(userService.findByEmail(anyString())).thenReturn(java.util.Optional.empty());

        mockMvc.perform(post("/api/food-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void testGetFoodLogs_success() throws Exception {
        when(userService.findByEmail(anyString())).thenReturn(java.util.Optional.of(user));
        when(foodLogsService.getFoodLogs(eq(user.getEmail()), anyString()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/food-logs")
                        .param("date", "2024-07-20"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = "test@test.com", roles = "USER")
    void testDeleteFoodLog_success() throws Exception {
        when(userService.findByEmail(anyString())).thenReturn(java.util.Optional.of(user));
        Mockito.doNothing().when(foodLogsService).deleteFoodLog(eq(1L), eq(user.getEmail()));

        mockMvc.perform(delete("/api/food-logs/1"))
                .andExpect(status().isNoContent());
    }
}
