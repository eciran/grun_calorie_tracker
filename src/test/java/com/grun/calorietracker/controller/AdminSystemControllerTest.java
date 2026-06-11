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
import java.util.Map;

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
                60000L,
                2,
                128L,
                512L,
                10L,
                0L,
                4L,
                0L,
                0L,
                false,
                "DISABLED",
                "not-configured",
                12L,
                1L,
                0.0833,
                40L,
                30L,
                5L,
                Map.of("IRRELEVANT_RESULT", 2L),
                5L,
                0.75,
                22L,
                8400L,
                11L,
                70L,
                List.of(),
                LocalDateTime.of(2026, 5, 27, 14, 0)
        );

        when(adminSystemHealthService.getHealth()).thenReturn(health);

        mockMvc.perform(get("/api/v1/admin/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.appName").value("grun-calorie-tracker"))
                .andExpect(jsonPath("$.databaseStatus").value("UP"))
                .andExpect(jsonPath("$.databaseLatencyMs").value(11))
                .andExpect(jsonPath("$.revenueCatEventsLast24h").value(10))
                .andExpect(jsonPath("$.failedRevenueCatEvents").value(0))
                .andExpect(jsonPath("$.activeSubscriptions").value(4))
                .andExpect(jsonPath("$.systemAlertsLast24h").value(0))
                .andExpect(jsonPath("$.aiRequestsLast24h").value(12))
                .andExpect(jsonPath("$.failedAiRequestsLast24h").value(1))
                .andExpect(jsonPath("$.aiDraftsLast7d").value(40))
                .andExpect(jsonPath("$.confirmedAiDraftsLast7d").value(30))
                .andExpect(jsonPath("$.aiRejectionReasonsLast7d.IRRELEVANT_RESULT").value(2))
                .andExpect(jsonPath("$.openAiDraftsLast7d").value(5))
                .andExpect(jsonPath("$.aiDraftConfirmationRateLast7d").value(0.75))
                .andExpect(jsonPath("$.logFlowCompletedLast24h").value(22))
                .andExpect(jsonPath("$.averageLogFlowDurationMsLast24h").value(8400))
                .andExpect(jsonPath("$.quickLogSuggestionAppliedLast24h").value(11))
                .andExpect(jsonPath("$.searchStartedLast24h").value(70))
                .andExpect(jsonPath("$.aiProvider").value("DISABLED"));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void getHealth_whenNotAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/system/health"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getHealth_whenJwtMalformed_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/system/health")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("JWT token is missing or invalid."));
    }
}
