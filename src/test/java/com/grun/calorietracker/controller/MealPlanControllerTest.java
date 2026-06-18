package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.GroceryListDto;
import com.grun.calorietracker.dto.GroceryListItemDto;
import com.grun.calorietracker.dto.MealPlanDto;
import com.grun.calorietracker.dto.MealPlanDuplicateRequestDto;
import com.grun.calorietracker.dto.MealPlanItemDto;
import com.grun.calorietracker.dto.MealPlanItemRequestDto;
import com.grun.calorietracker.dto.MealPlanRequestDto;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.MealPlanItemType;
import com.grun.calorietracker.enums.MealPlanStatus;
import com.grun.calorietracker.service.MealPlanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
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
        response.setItems(List.of(new GroceryListItemDto(1L, "Greek yogurt", 340.0, 2)));
        when(mealPlanService.getGroceryList("user@grun.app", 10L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/meal-plans/10/grocery-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].totalGrams").value(340.0));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void archiveMealPlan_returnsNoContent() throws Exception {
        doNothing().when(mealPlanService).archiveMealPlan("user@grun.app", 10L);

        mockMvc.perform(delete("/api/v1/meal-plans/10"))
                .andExpect(status().isNoContent());
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
