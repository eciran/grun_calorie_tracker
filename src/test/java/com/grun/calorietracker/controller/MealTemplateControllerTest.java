package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.dto.MealTemplateApplyRequestDto;
import com.grun.calorietracker.dto.MealTemplateCreateRequestDto;
import com.grun.calorietracker.dto.MealTemplateDto;
import com.grun.calorietracker.dto.MealTemplateItemRequestDto;
import com.grun.calorietracker.dto.MealTemplateUpdateRequestDto;
import com.grun.calorietracker.service.MealTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MealTemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private MealTemplateService mealTemplateService;

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void createTemplate_returnsSavedTemplate() throws Exception {
        MealTemplateCreateRequestDto request = new MealTemplateCreateRequestDto();
        request.setName("Breakfast");
        request.setSourceDate(LocalDate.of(2026, 5, 21));
        request.setMealType("BREAKFAST");
        MealTemplateDto template = new MealTemplateDto();
        template.setId(3L);
        template.setName("Breakfast");
        template.setMealType("BREAKFAST");
        when(mealTemplateService.createFromLoggedMeal(eq("user@test.com"), any())).thenReturn(template);

        mockMvc.perform(post("/api/v1/meal-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3L))
                .andExpect(jsonPath("$.mealType").value("BREAKFAST"));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void updateTemplate_returnsReplacementTemplate() throws Exception {
        MealTemplateItemRequestDto item = new MealTemplateItemRequestDto();
        item.setFoodItemId(4L);
        item.setPortionSize(120.0);
        MealTemplateUpdateRequestDto request = new MealTemplateUpdateRequestDto();
        request.setName("Lunch");
        request.setMealType("LUNCH");
        request.setItems(java.util.List.of(item));
        MealTemplateDto template = new MealTemplateDto();
        template.setId(3L);
        template.setName("Lunch");
        template.setMealType("LUNCH");
        when(mealTemplateService.updateTemplate(eq("user@test.com"), eq(3L), any())).thenReturn(template);

        mockMvc.perform(put("/api/v1/meal-templates/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lunch"))
                .andExpect(jsonPath("$.mealType").value("LUNCH"));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void applyTemplate_mobileAliasReturnsLoggedItems() throws Exception {
        MealTemplateApplyRequestDto request = new MealTemplateApplyRequestDto();
        request.setTargetDate(LocalDate.of(2026, 5, 28));
        request.setMealType("LUNCH");

        FoodLogsDto logged = new FoodLogsDto();
        logged.setId(90L);
        logged.setFoodItemId(4L);
        logged.setMealType("LUNCH");
        when(mealTemplateService.applyTemplate(eq("user@test.com"), eq(3L), any())).thenReturn(java.util.List.of(logged));

        mockMvc.perform(post("/api/v1/meal-templates/3/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(90L))
                .andExpect(jsonPath("$[0].mealType").value("LUNCH"));
    }
}
