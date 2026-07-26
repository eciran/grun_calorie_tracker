package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AiInsightResponseDto;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.service.AiInsightService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiInsightControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiInsightService aiInsightService;

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void dailyInsight_returnsControlledInsight() throws Exception {
        when(aiInsightService.createDailyInsight(eq("user@example.com"), eq("daily-request-123"), any())).thenReturn(response(AiRequestType.AI_DAILY_INSIGHT));

        mockMvc.perform(post("/api/v1/ai/insights/daily")
                        .header("Idempotency-Key", "daily-request-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date": "2026-07-02",
                                  "focus": "PROTEIN",
                                  "language": "en"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestType").value("AI_DAILY_INSIGHT"))
                .andExpect(jsonPath("$.recommendedActions[0]").value("Review protein at dinner."));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void weeklyInsight_returnsControlledInsight() throws Exception {
        when(aiInsightService.createWeeklyInsight(eq("user@example.com"), eq("weekly-request-123"), any())).thenReturn(response(AiRequestType.AI_WEEKLY_INSIGHT));

        mockMvc.perform(post("/api/v1/ai/insights/weekly")
                        .header("Idempotency-Key", "weekly-request-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-06-26",
                                  "endDate": "2026-07-02",
                                  "language": "en"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestType").value("AI_WEEKLY_INSIGHT"));
    }

    private AiInsightResponseDto response(AiRequestType type) {
        AiInsightResponseDto dto = new AiInsightResponseDto();
        dto.setRequestId(12L);
        dto.setRequestType(type);
        dto.setStatus(AiRequestStatus.DRAFT_CREATED);
        dto.setProvider(AiProvider.LOG);
        dto.setModel("log-draft-v1");
        dto.setTitle("Insight");
        dto.setSummary("Controlled app-scoped feedback.");
        dto.setHighlights(List.of("Protein was below target."));
        dto.setRecommendedActions(List.of("Review protein at dinner."));
        dto.setAiRemainingThisPeriod(8);
        return dto;
    }
}
