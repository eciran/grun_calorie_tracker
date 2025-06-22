package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.entity.ProgressLogEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.service.ProgressLogService;
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

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProgressLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProgressLogService progressLogService;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    private UserEntity mockUser;
    private ProgressLogEntity logRequest;

    @BeforeEach
    void setUp() {
        mockUser = new UserEntity();
        mockUser.setId(1L);
        mockUser.setEmail("testuser@example.com");
        mockUser.setPassword("password");

        logRequest = new ProgressLogEntity();
        logRequest.setLogDate(LocalDate.now().atStartOfDay());
        logRequest.setWeight(82.5);
        logRequest.setCalorieIntake(2100);
        logRequest.setProteinIntake(160.0);
        logRequest.setFatIntake(70.0);
        logRequest.setCarbIntake(240.0);
    }

    @Test
    @WithMockUser(username = "testuser@example.com")
    void testAddProgressLog_Success() throws Exception {
        when(userService.findByEmail("testuser@example.com")).thenReturn(Optional.of(mockUser));

        mockMvc.perform(post("/api/progress/saveLogs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logRequest)))
                .andExpect(status().isOk());
    }

    @Test
    void testAddProgressLog_Unauthorized() throws Exception {
        mockMvc.perform(post("/api/progress/saveLogs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "testuser@example.com")
    void testGetProgress_Success() throws Exception {
        when(userService.findByEmail("testuser@example.com")).thenReturn(Optional.of(mockUser));

        mockMvc.perform(get("/api/progress/getLogs"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetProgress_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/progress/getLogs"))
                .andExpect(status().isForbidden());
    }
}
