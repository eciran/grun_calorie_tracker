package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AiRecipeDraftResponseDto;
import com.grun.calorietracker.dto.RecipeDto;
import com.grun.calorietracker.dto.RecipeNutritionDto;
import com.grun.calorietracker.dto.RecipeRequestDto;
import com.grun.calorietracker.dto.RecipeStepRequestDto;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.service.AiRecipeDraftService;
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
class AiRecipeDraftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiRecipeDraftService aiRecipeDraftService;

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void generateRecipeDraft_returnsDraft() throws Exception {
        AiRecipeDraftResponseDto response = new AiRecipeDraftResponseDto();
        response.setRequestId(55L);
        response.setRequestType(AiRequestType.AI_RECIPE_GENERATION);
        response.setStatus(AiRequestStatus.DRAFT_CREATED);
        response.setProvider(AiProvider.LOG);
        response.setModel("log-draft-v1");
        response.setSuggestedRecipe(recipeRequest());
        response.setEstimatedNutritionTotal(nutrition());
        response.setEstimatedNutritionPerServing(nutrition());
        response.setNutritionEstimateNote("Estimated preview nutrition. Confirmed recipe nutrition is recalculated after save.");
        response.setAiRemainingThisPeriod(8);
        when(aiRecipeDraftService.createRecipeDraft(eq("user@example.com"), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/ai/recipes/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt": "High protein chicken dinner",
                                  "mealType": "DINNER",
                                  "marketRegion": "TR",
                                  "language": "tr",
                                  "servingCount": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(55))
                .andExpect(jsonPath("$.requestType").value("AI_RECIPE_GENERATION"))
                .andExpect(jsonPath("$.suggestedRecipe.name").value("Chicken dinner"))
                .andExpect(jsonPath("$.suggestedRecipe.cookingSteps[0].instruction").value("Cook the chicken until done."))
                .andExpect(jsonPath("$.estimatedNutritionPerServing.calories").value(420.0))
                .andExpect(jsonPath("$.nutritionEstimateNote").exists())
                .andExpect(jsonPath("$.aiRemainingThisPeriod").value(8));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void confirmRecipeDraft_returnsCreatedRecipe() throws Exception {
        RecipeDto recipe = new RecipeDto();
        recipe.setId(77L);
        recipe.setName("Reviewed recipe");
        when(aiRecipeDraftService.confirmRecipeDraft(eq("user@example.com"), eq(55L), any())).thenReturn(recipe);

        mockMvc.perform(post("/api/v1/ai/recipes/55/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipe": {
                                    "name": "Reviewed recipe",
                                    "mealType": "DINNER",
                                    "totalYieldGrams": 150,
                                    "defaultServingGrams": 150,
                                    "servingCount": 1,
                                    "ingredients": [
                                      {
                                        "foodItemId": 2,
                                        "portionSize": 150,
                                        "portionUnit": "GRAM"
                                      }
                                    ]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.name").value("Reviewed recipe"));
    }

    private RecipeRequestDto recipeRequest() {
        RecipeRequestDto request = new RecipeRequestDto();
        request.setName("Chicken dinner");
        request.setMealType("DINNER");
        request.setServingCount(2);
        request.setCookingSteps(List.of(step("Cook the chicken until done."), step("Serve with vegetables.")));
        return request;
    }

    private RecipeNutritionDto nutrition() {
        return new RecipeNutritionDto(420.0, 38.0, 32.0, 14.0, 6.0, 5.0, 3.0, 520.0, 850.0, 90.0, 110.0, 3.2, 70.0, 2.1, 430.0, 28.0, 1.5, 2.4, 1.2);
    }

    private RecipeStepRequestDto step(String instruction) {
        RecipeStepRequestDto step = new RecipeStepRequestDto();
        step.setInstruction(instruction);
        return step;
    }
}