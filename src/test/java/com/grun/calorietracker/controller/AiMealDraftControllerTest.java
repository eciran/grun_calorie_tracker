package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AiMealDraftItemDto;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.AiMealDraftConfirmResponseDto;
import com.grun.calorietracker.dto.AiRequestHistoryDto;
import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiDraftRejectReason;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.service.AiMealDraftService;
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
class AiMealDraftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiMealDraftService aiMealDraftService;

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void createVoiceDraft_returnsDraft() throws Exception {
        AiMealDraftResponseDto response = response(AiRequestType.VOICE_FOOD_LOG);
        when(aiMealDraftService.createVoiceFoodDraft(eq("user@example.com"), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/ai/meal-drafts/voice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "transcript": "I ate chicken and rice",
                                  "mealType": "LUNCH",
                                  "logDate": "2026-06-01T13:30:00",
                                  "locale": "en"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestType").value("VOICE_FOOD_LOG"))
                .andExpect(jsonPath("$.provider").value("LOG"))
                .andExpect(jsonPath("$.items[0].name").value("Chicken and rice"));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void createPhotoDraft_requiresImageReference() throws Exception {
        mockMvc.perform(post("/api/v1/ai/meal-drafts/photo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userNote": "plate"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void listHistory_returnsUserHistory() throws Exception {
        when(aiMealDraftService.listHistory("user@example.com", 10)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/ai/meal-drafts/history"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void confirmDraft_returnsCreatedLogs() throws Exception {
        FoodLogsDto log = new FoodLogsDto();
        log.setId(99L);
        log.setFoodItemId(12L);
        log.setFoodName("Chicken");

        AiMealDraftConfirmResponseDto response = new AiMealDraftConfirmResponseDto();
        response.setRequestId(10L);
        response.setStatus(AiRequestStatus.CONFIRMED);
        response.setCreatedLogs(List.of(log));
        when(aiMealDraftService.confirmDraft(eq("user@example.com"), eq(10L), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/ai/meal-drafts/10/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "foodItemId": 12,
                                      "portionSize": 150,
                                      "portionUnit": "GRAM",
                                      "mealType": "LUNCH",
                                      "logDate": "2026-06-01T13:30:00"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.createdLogs[0].id").value(99));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void rejectDraft_closesDraft() throws Exception {
        AiRequestHistoryDto response = new AiRequestHistoryDto();
        response.setId(10L);
        response.setStatus(AiRequestStatus.REJECTED);
        response.setRejectionReason(AiDraftRejectReason.IRRELEVANT_RESULT);
        response.setHasRejectionFeedback(true);
        when(aiMealDraftService.rejectDraft(eq("user@example.com"), eq(10L), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/ai/meal-drafts/10/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "IRRELEVANT_RESULT",
                                  "feedback": "The result was not related to my meal."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("IRRELEVANT_RESULT"))
                .andExpect(jsonPath("$.hasRejectionFeedback").value(true));
    }

    private AiMealDraftResponseDto response(AiRequestType type) {
        AiMealDraftItemDto item = new AiMealDraftItemDto();
        item.setName("Chicken and rice");
        item.setQuantity(100.0);
        item.setUnit("g");
        item.setEstimatedCalories(150.0);
        item.setConfidence(0.7);

        AiMealDraftResponseDto response = new AiMealDraftResponseDto();
        response.setRequestId(1L);
        response.setRequestType(type);
        response.setProvider(AiProvider.LOG);
        response.setModel("log-draft-v1");
        response.setStatus(AiRequestStatus.DRAFT_CREATED);
        response.setSuggestedMealType("LUNCH");
        response.setSuggestedLogDate(LocalDateTime.of(2026, 6, 1, 13, 30));
        response.setSummary("Draft");
        response.setItems(List.of(item));
        response.setAiRemainingThisPeriod(14);
        return response;
    }
}
