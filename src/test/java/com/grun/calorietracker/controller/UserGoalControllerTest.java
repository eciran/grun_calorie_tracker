package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.dto.UserGoalDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;
import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.enums.GoalType;
import com.grun.calorietracker.service.UserGoalService;
import com.grun.calorietracker.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserGoalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private UserGoalService goalService;

    @Autowired
    private ObjectMapper objectMapper;

    private UserEntity mockUser;
    private UserGoalEntity goalRequest;

    @BeforeEach
    void setUp() {
        mockUser = new UserEntity();
        mockUser.setId(1L);
        mockUser.setName("Test User");
        mockUser.setEmail("testuser@example.com");
        mockUser.setPassword("password");
        mockUser.setAge(30);
        mockUser.setGender("MALE");
        mockUser.setHeight(180.0);
        mockUser.setWeight(80.0);

        goalRequest = new UserGoalEntity();
        goalRequest.setTargetWeight(75.0);
        goalRequest.setActivityLevel(ActivityLevel.MODERATE);
        goalRequest.setGoalType(GoalType.LOSE_WEIGHT);
    }

    @Test
    @WithMockUser(username = "testuser@example.com")
    void testSaveGoal_Success() throws Exception {
        when(userService.findByEmail("testuser@example.com")).thenReturn(Optional.of(mockUser));
        when(goalService.calculateGoal(any(UserGoalDto.class), any(String))).thenReturn(new GoalCalculationResponse());

        mockMvc.perform(post("/api/goals/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goalRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "testuser@example.com")
    void testSaveGoal_UserNotFound() throws Exception {
        when(userService.findByEmail("testuser@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/goals/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goalRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser@example.com")
    void testSaveGoal_InvalidEmail() throws Exception {
        mockUser.setEmail(null);
        when(userService.findByEmail("testuser@example.com")).thenReturn(Optional.of(mockUser));

        mockMvc.perform(post("/api/goals/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goalRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testSaveGoal_Unauthorized() throws Exception {
        mockMvc.perform(post("/api/goals/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goalRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "testuser@example.com")
    void testDeleteGoal_Success() throws Exception {
        when(userService.findByEmail("testuser@example.com")).thenReturn(Optional.of(mockUser));

        mockMvc.perform(delete("/api/goals/delete"))
                .andExpect(status().isOk());
    }

    @Test
    void testDeleteGoal_Unauthorized() throws Exception {
        mockMvc.perform(delete("/api/goals/delete"))
                .andExpect(status().isForbidden());
    }
}
