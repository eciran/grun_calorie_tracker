package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AiNutritionPlanCreditEstimateDto;
import com.grun.calorietracker.dto.AiNutritionPlanDraftResponseDto;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.enums.NutritionPlanGenerationMode;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.service.AiNutritionPlanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiNutritionPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiNutritionPlanService service;

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void estimateCreditCost_returnsBackendAuthoritativeWeightedCost() throws Exception {
        when(service.estimateCreditCost("user@example.com", 7, 4, NutritionPlanGenerationMode.GENERAL))
                .thenReturn(new AiNutritionPlanCreditEstimateDto(
                        7, 4, NutritionPlanGenerationMode.GENERAL, 3, 28, 4, 8, 3, false, 0, 6));

        mockMvc.perform(get("/api/v1/ai/nutrition-plans/credit-cost")
                        .param("dayCount", "7")
                        .param("mealsPerDay", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayCount").value(7))
                .andExpect(jsonPath("$.generationMode").value("GENERAL"))
                .andExpect(jsonPath("$.baseCreditCost").value(3))
                .andExpect(jsonPath("$.mealsPerDay").value(4))
                .andExpect(jsonPath("$.totalMealSlots").value(28))
                .andExpect(jsonPath("$.includedMealSlots").value(4))
                .andExpect(jsonPath("$.mealSlotsPerAdditionalCredit").value(8))
                .andExpect(jsonPath("$.additionalCredits").value(3))
                .andExpect(jsonPath("$.workoutContextIncluded").value(false))
                .andExpect(jsonPath("$.workoutContextCreditCost").value(0))
                .andExpect(jsonPath("$.totalCreditCost").value(6));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void estimateCreditCost_forWorkoutAlignedPlan_includesWorkoutContextSurcharge() throws Exception {
        when(service.estimateCreditCost("user@example.com", 7, 4, NutritionPlanGenerationMode.WORKOUT_ALIGNED))
                .thenReturn(new AiNutritionPlanCreditEstimateDto(
                        7, 4, NutritionPlanGenerationMode.WORKOUT_ALIGNED, 3, 28, 4, 8, 3, true, 3, 9));

        mockMvc.perform(get("/api/v1/ai/nutrition-plans/credit-cost")
                        .param("dayCount", "7")
                        .param("mealsPerDay", "4")
                        .param("generationMode", "WORKOUT_ALIGNED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generationMode").value("WORKOUT_ALIGNED"))
                .andExpect(jsonPath("$.workoutContextIncluded").value(true))
                .andExpect(jsonPath("$.workoutContextCreditCost").value(3))
                .andExpect(jsonPath("$.totalCreditCost").value(9));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void generate_returnsDraftAndForwardsIdempotencyKey() throws Exception {
        AiNutritionPlanDraftResponseDto response = new AiNutritionPlanDraftResponseDto();
        response.setRequestId(91L);
        response.setRequestType(AiRequestType.AI_NUTRITION_PLAN);
        response.setStatus(AiRequestStatus.DRAFT_CREATED);
        response.setProvider(AiProvider.LOG);
        response.setModel("log-nutrition-v1");
        response.setGenerationMode(NutritionPlanGenerationMode.GENERAL);
        response.setName("Balanced Nutrition Plan");
        response.setQuotaConsumedAmount(8);
        response.setAiBaseRemainingThisPeriod(8);
        response.setAiAddonRemainingThisPeriod(0);
        response.setAiRemainingThisPeriod(8);
        when(service.createDraft(eq("user@example.com"), eq("mobile-plan-001"), any()))
                .thenReturn(response);

        String body = """
                {
                  "generationMode": "GENERAL",
                  "startDate": "%s",
                  "dayCount": 7,
                  "mealsPerDay": 4,
                  "excludedFoods": [],
                  "dietaryPreferences": ["HIGH_PROTEIN"],
                  "language": "en"
                }
                """.formatted(LocalDate.now().plusDays(1));

        mockMvc.perform(post("/api/v1/ai/nutrition-plans/generate")
                        .header("Idempotency-Key", "mobile-plan-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(91))
                .andExpect(jsonPath("$.requestType").value("AI_NUTRITION_PLAN"))
                .andExpect(jsonPath("$.generationMode").value("GENERAL"))
                .andExpect(jsonPath("$.quotaConsumedAmount").value(8))
                .andExpect(jsonPath("$.aiBaseRemainingThisPeriod").value(8))
                .andExpect(jsonPath("$.aiAddonRemainingThisPeriod").value(0))
                .andExpect(jsonPath("$.aiRemainingThisPeriod").value(8));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void generate_whenSameKeyIsProcessing_returnsConflictWithoutTechnicalDetails() throws Exception {
        when(service.createDraft(eq("user@example.com"), eq("mobile-plan-race"), any()))
                .thenThrow(new RequestConflictException(
                        "An AI nutrition-plan request with this key is already processing."));

        mockMvc.perform(post("/api/v1/ai/nutrition-plans/generate")
                        .header("Idempotency-Key", "mobile-plan-race")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "generationMode": "GENERAL",
                                  "startDate": "2099-01-01",
                                  "dayCount": 7,
                                  "mealsPerDay": 4
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Request conflict"))
                .andExpect(jsonPath("$.message").value(
                        "An AI nutrition-plan request with this key is already processing."));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void generate_withoutIdempotencyKey_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/ai/nutrition-plans/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "generationMode": "GENERAL",
                                  "startDate": "2099-01-01",
                                  "dayCount": 7,
                                  "mealsPerDay": 4
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}