package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminSystemHealthDto;
import com.grun.calorietracker.service.AdminSystemHealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminSystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminSystemHealthService adminSystemHealthService;

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getHealth_whenAdmin_returnsSystemHealth() throws Exception {
        AdminSystemHealthDto health = new AdminSystemHealthDto(
                "UP",
                "grun-calorie-tracker",
                "0.0.1-SNAPSHOT",
                List.of("prod"),
                "UP",
                11L,
                LocalDateTime.of(2026, 5, 27, 14, 0)
        );

        when(adminSystemHealthService.getHealth()).thenReturn(health);

        mockMvc.perform(get("/api/v1/admin/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.appName").value("grun-calorie-tracker"))
                .andExpect(jsonPath("$.databaseStatus").value("UP"))
                .andExpect(jsonPath("$.databaseLatencyMs").value(11));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void getHealth_whenNotAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/system/health"))
                .andExpect(status().isForbidden());
    }
}
