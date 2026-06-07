package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AiRecipeDraftResponseDto;
import com.grun.calorietracker.dto.RecipeDto;
import com.grun.calorietracker.dto.RecipeRequestDto;
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
        return request;
    }
}
