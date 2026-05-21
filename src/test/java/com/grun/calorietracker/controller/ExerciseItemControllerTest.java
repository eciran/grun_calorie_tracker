package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.ExerciseItemDto;
import com.grun.calorietracker.dto.ExerciseItemPageDto;
import com.grun.calorietracker.enums.ExerciseDifficulty;
import com.grun.calorietracker.exception.DuplicateExerciseItemException;
import com.grun.calorietracker.service.ExerciseItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
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
class ExerciseItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExerciseItemService exerciseItemService;

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void getAllItems_whenAuthenticatedUser_returnsItems() throws Exception {
        ExerciseItemDto item = new ExerciseItemDto();
        item.setId(1L);
        item.setName("Running");
        item.setMetCode("RUNNING_GENERAL");

        ExerciseItemPageDto page = new ExerciseItemPageDto();
        page.setContent(List.of(item));
        page.setPage(0);
        page.setSize(25);
        page.setTotalElements(1L);
        page.setTotalPages(1);
        page.setFirst(true);
        page.setLast(true);

        when(exerciseItemService.searchItems(
                eq("run"),
                eq("Lower Body"),
                eq("None"),
                eq(ExerciseDifficulty.INTERMEDIATE),
                eq(true),
                eq(0),
                eq(25)
        )).thenReturn(page);

        mockMvc.perform(get("/api/v1/exercise-items")
                        .param("q", "run")
                        .param("primaryMuscleGroup", "Lower Body")
                        .param("equipment", "None")
                        .param("difficulty", "INTERMEDIATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Running"))
                .andExpect(jsonPath("$.content[0].metCode").value("RUNNING_GENERAL"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void addExerciseItem_whenNotAdmin_returnsForbidden() throws Exception {
        ExerciseItemDto request = buildRequest();

        mockMvc.perform(post("/api/v1/exercise-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void addExerciseItem_whenAdmin_returnsCreatedItem() throws Exception {
        ExerciseItemDto request = buildRequest();
        ExerciseItemDto response = buildRequest();
        response.setId(10L);

        when(exerciseItemService.addItem(any(ExerciseItemDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/exercise-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.metCode").value("RUNNING_GENERAL"));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void addExerciseItem_whenMetCodeExists_returnsConflict() throws Exception {
        ExerciseItemDto request = buildRequest();

        when(exerciseItemService.addItem(any(ExerciseItemDto.class)))
                .thenThrow(new DuplicateExerciseItemException("Exercise item metCode already exists: RUNNING_GENERAL"));

        mockMvc.perform(post("/api/v1/exercise-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Duplicate exercise item"));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void addExerciseItem_whenRequiredFieldsMissing_returnsBadRequest() throws Exception {
        ExerciseItemDto request = new ExerciseItemDto();

        mockMvc.perform(post("/api/v1/exercise-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation error"))
                .andExpect(jsonPath("$.path").value("/api/v1/exercise-items"));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void getAllItems_whenPageSizeTooLarge_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/exercise-items")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation error"))
                .andExpect(jsonPath("$.path").value("/api/v1/exercise-items"));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void updateExerciseItem_whenNotAdmin_returnsForbidden() throws Exception {
        ExerciseItemDto request = buildRequest();

        mockMvc.perform(put("/api/v1/exercise-items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void deleteExerciseItem_whenNotAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/exercise-items/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void deleteExerciseItem_whenAdmin_returnsNoContent() throws Exception {
        doNothing().when(exerciseItemService).deleteItem(1L);

        mockMvc.perform(delete("/api/v1/exercise-items/1"))
                .andExpect(status().isNoContent());
    }

    private ExerciseItemDto buildRequest() {
        ExerciseItemDto request = new ExerciseItemDto();
        request.setName("Running");
        request.setMetCode("RUNNING_GENERAL");
        request.setCaloriesPerMinute(10.5);
        return request;
    }
}

