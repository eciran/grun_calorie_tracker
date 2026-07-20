package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.GroceryListDto;
import com.grun.calorietracker.dto.GroceryListItemDto;
import com.grun.calorietracker.dto.MealPlanDto;
import com.grun.calorietracker.dto.MealPlanDuplicateRequestDto;
import com.grun.calorietracker.dto.MealPlanItemDto;
import com.grun.calorietracker.dto.MealPlanRecipeCandidateDto;
import com.grun.calorietracker.dto.MealPlanRecipeLinkDto;
import com.grun.calorietracker.dto.MealPlanItemConsumptionDto;
import com.grun.calorietracker.dto.MealPlanItemLogRequestDto;
import com.grun.calorietracker.dto.MealPlanTodayDto;
import com.grun.calorietracker.dto.MealPlanItemRequestDto;
import com.grun.calorietracker.dto.MealPlanRequestDto;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.MealPlanItemType;
import com.grun.calorietracker.enums.MealPlanStatus;
import com.grun.calorietracker.service.MealPlanService;
import com.grun.calorietracker.service.MealPlanRecipeLinkService;
import com.grun.calorietracker.service.MealPlanTrackingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MealPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MealPlanService mealPlanService;

    @MockitoBean
    private MealPlanTrackingService mealPlanTrackingService;

    @MockitoBean
    private MealPlanRecipeLinkService mealPlanRecipeLinkService;

    @Test
    @WithMockUser(username = "user@grun.app")
    void createMealPlan_returnsCreatedPlan() throws Exception {
        MealPlanRequestDto request = request();
        when(mealPlanService.createMealPlan(any(), any(MealPlanRequestDto.class))).thenReturn(response(10L, "Week plan"));

        mockMvc.perform(post("/api/v1/meal-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.items[0].foodItemName").value("Greek yogurt"));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void listMealPlans_returnsPlans() throws Exception {
        when(mealPlanService.getMealPlans("user@grun.app")).thenReturn(List.of(response(10L, "Week plan")));

        mockMvc.perform(get("/api/v1/meal-plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void getMealPlan_returnsPlan() throws Exception {
        when(mealPlanService.getMealPlan("user@grun.app", 10L)).thenReturn(response(10L, "Week plan"));

        mockMvc.perform(get("/api/v1/meal-plans/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Week plan"));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void updateMealPlan_returnsUpdatedPlan() throws Exception {
        when(mealPlanService.updateMealPlan(any(), any(), any(MealPlanRequestDto.class))).thenReturn(response(10L, "Updated"));

        mockMvc.perform(put("/api/v1/meal-plans/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void duplicateMealPlan_returnsCopiedPlan() throws Exception {
        MealPlanDuplicateRequestDto request = new MealPlanDuplicateRequestDto();
        request.setName("Next week");
        request.setStartDate(LocalDate.of(2026, 6, 22));
        when(mealPlanService.duplicateMealPlan(any(), any(), any(MealPlanDuplicateRequestDto.class)))
                .thenReturn(response(11L, "Next week"));

        mockMvc.perform(post("/api/v1/meal-plans/10/duplicate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11L))
                .andExpect(jsonPath("$.name").value("Next week"));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void getGroceryList_returnsAggregatedItems() throws Exception {
        GroceryListDto response = new GroceryListDto();
        response.setMealPlanId(10L);
        response.setMealPlanName("Week plan");
        response.setItems(List.of(new GroceryListItemDto(1L, "Greek yogurt", 340.0, 2.0, FoodPortionUnit.SERVING, 2)));
        when(mealPlanService.getGroceryList("user@grun.app", 10L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/meal-plans/10/grocery-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].totalGrams").value(340.0))
                .andExpect(jsonPath("$.items[0].totalQuantity").value(2.0))
                .andExpect(jsonPath("$.items[0].quantityUnit").value("SERVING"))
                .andExpect(jsonPath("$.items[0].plannedUses").value(2));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void archiveMealPlan_returnsNoContent() throws Exception {
        doNothing().when(mealPlanService).archiveMealPlan("user@grun.app", 10L);

        mockMvc.perform(delete("/api/v1/meal-plans/10"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void activateMealPlan_returnsActivePlan() throws Exception {
        MealPlanDto response = response(10L, "Week plan");
        response.setStatus(MealPlanStatus.ACTIVE);
        when(mealPlanTrackingService.activate("user@grun.app", 10L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/meal-plans/10/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void getActiveToday_returnsTrackingView() throws Exception {
        MealPlanTodayDto response = new MealPlanTodayDto();
        response.setPlanId(10L);
        response.setDate(LocalDate.of(2026, 7, 15));
        response.setMeals(List.of());
        when(mealPlanTrackingService.getActiveForDate(
                "user@grun.app", LocalDate.of(2026, 7, 15))).thenReturn(response);

        mockMvc.perform(get("/api/v1/meal-plans/active/today")
                        .param("date", "2026-07-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(10L))
                .andExpect(jsonPath("$.date").value("2026-07-15"));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void logPlannedItem_requiresIdempotencyKey() throws Exception {
        MealPlanItemLogRequestDto request = new MealPlanItemLogRequestDto();
        request.setConsumedQuantity(120.0);
        request.setConsumedUnit(FoodPortionUnit.GRAM);
        request.setLoggedAt(LocalDateTime.of(2026, 7, 15, 12, 30));

        mockMvc.perform(post("/api/v1/meal-plans/10/items/100/log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
        verifyNoInteractions(mealPlanTrackingService);
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void logPlannedItem_returnsConsumption() throws Exception {
        MealPlanItemLogRequestDto request = new MealPlanItemLogRequestDto();
        request.setConsumedQuantity(120.0);
        request.setConsumedUnit(FoodPortionUnit.GRAM);
        request.setLoggedAt(LocalDateTime.of(2026, 7, 15, 12, 30));
        MealPlanItemConsumptionDto response = new MealPlanItemConsumptionDto();
        response.setId(501L);
        response.setMealPlanItemId(100L);
        response.setStatus(com.grun.calorietracker.enums.MealPlanItemConsumptionStatus.PARTIALLY_CONSUMED);
        when(mealPlanTrackingService.logItem(
                any(), any(), any(), any(), any(MealPlanItemLogRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/meal-plans/10/items/100/log")
                        .header("Idempotency-Key", "meal-key-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(501L))
                .andExpect(jsonPath("$.status").value("PARTIALLY_CONSUMED"));
    }
    @Test
    @WithMockUser(username = "user@grun.app")
    void findRecipeCandidates_returnsOptionalCandidates() throws Exception {
        MealPlanRecipeCandidateDto candidate = new MealPlanRecipeCandidateDto();
        candidate.setRecipeId(25L);
        candidate.setName("Chicken Rice Bowl");
        candidate.setExactNameMatch(true);
        candidate.setMatchScore(100);
        when(mealPlanRecipeLinkService.findCandidates("user@grun.app", 10L, 100L, 3))
                .thenReturn(List.of(candidate));

        mockMvc.perform(get("/api/v1/meal-plans/10/items/100/recipe-link/candidates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recipeId").value(25L))
                .andExpect(jsonPath("$[0].exactNameMatch").value(true))
                .andExpect(jsonPath("$[0].matchScore").value(100));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void linkRecipe_returnsUserConfirmedNavigation() throws Exception {
        MealPlanRecipeLinkDto link = new MealPlanRecipeLinkDto();
        link.setMealPlanId(10L);
        link.setMealPlanItemId(100L);
        link.setRecipeId(25L);
        link.setRecipeNavigationAvailable(true);
        when(mealPlanRecipeLinkService.linkRecipe("user@grun.app", 10L, 100L, 25L)).thenReturn(link);

        mockMvc.perform(post("/api/v1/meal-plans/10/items/100/recipe-link/25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipeId").value(25L))
                .andExpect(jsonPath("$.recipeNavigationAvailable").value(true));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void unlinkRecipe_removesOnlyNavigation() throws Exception {
        MealPlanRecipeLinkDto link = new MealPlanRecipeLinkDto();
        link.setMealPlanId(10L);
        link.setMealPlanItemId(100L);
        link.setRecipeNavigationAvailable(false);
        when(mealPlanRecipeLinkService.unlinkRecipe("user@grun.app", 10L, 100L)).thenReturn(link);

        mockMvc.perform(delete("/api/v1/meal-plans/10/items/100/recipe-link"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipeId").doesNotExist())
                .andExpect(jsonPath("$.recipeNavigationAvailable").value(false));
    }
    private MealPlanRequestDto request() {
        MealPlanRequestDto request = new MealPlanRequestDto();
        request.setName("Week plan");
        request.setStartDate(LocalDate.of(2026, 6, 15));
        request.setEndDate(LocalDate.of(2026, 6, 21));
        MealPlanItemRequestDto item = new MealPlanItemRequestDto();
        item.setPlanDate(LocalDate.of(2026, 6, 15));
        item.setMealType("BREAKFAST");
        item.setItemType(MealPlanItemType.FOOD_ITEM);
        item.setFoodItemId(1L);
        item.setPortionSize(2.0);
        item.setPortionUnit(FoodPortionUnit.SERVING);
        request.setItems(List.of(item));
        return request;
    }

    private MealPlanDto response(Long id, String name) {
        MealPlanDto response = new MealPlanDto();
        response.setId(id);
        response.setName(name);
        response.setStartDate(LocalDate.of(2026, 6, 15));
        response.setEndDate(LocalDate.of(2026, 6, 21));
        response.setStatus(MealPlanStatus.DRAFT);
        MealPlanItemDto item = new MealPlanItemDto();
        item.setId(100L);
        item.setPlanDate(LocalDate.of(2026, 6, 15));
        item.setMealType("BREAKFAST");
        item.setItemType(MealPlanItemType.FOOD_ITEM);
        item.setFoodItemId(1L);
        item.setFoodItemName("Greek yogurt");
        item.setPortionSize(2.0);
        item.setPortionUnit(FoodPortionUnit.SERVING);
        response.setItems(List.of(item));
        return response;
    }
}
