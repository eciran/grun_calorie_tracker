package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.dto.OnboardingCompleteRequestDto;
import com.grun.calorietracker.dto.OnboardingCompleteResponseDto;
import com.grun.calorietracker.dto.UserGoalDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.enums.GoalType;
import com.grun.calorietracker.service.OnboardingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OnboardingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OnboardingService onboardingService;

    @Test
    @WithMockUser(username = "user@example.com")
    void completeOnboarding_whenRequestIsValid_returnsProfileGoalAndCalculation() throws Exception {
        OnboardingCompleteRequestDto request = validRequest();
        UserProfileDto profile = UserProfileDto.builder()
                .email("user@example.com")
                .name("Emrah")
                .age(32)
                .gender("MALE")
                .height(180.0)
                .weight(82.0)
                .bodyFat(19.2)
                .build();
        UserGoalDto goal = new UserGoalDto();
        goal.setDailyCalorieGoal(2209);
        goal.setDailyProteinGoal(138.0);
        goal.setDailyFatGoal(74.0);
        goal.setDailyCarbGoal(248.0);
        OnboardingCompleteResponseDto response = new OnboardingCompleteResponseDto(
                profile,
                goal,
                new GoalCalculationResponse(2209, 138, 74, 248),
                true
        );
        when(onboardingService.completeOnboarding(any(OnboardingCompleteRequestDto.class), eq("user@example.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/onboarding/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.profile.name").value("Emrah"))
                .andExpect(jsonPath("$.calculation.calculatedCalorieNeed").value(2209));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void completeOnboarding_whenRequiredFieldsAreMissing_returnsBadRequest() throws Exception {
        OnboardingCompleteRequestDto request = new OnboardingCompleteRequestDto();

        mockMvc.perform(post("/api/v1/onboarding/complete")
                        .header("Accept-Language", "tr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Dogrulama hatasi"));
    }

    @Test
    void completeOnboarding_whenUnauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    private OnboardingCompleteRequestDto validRequest() {
        OnboardingCompleteRequestDto request = new OnboardingCompleteRequestDto();
        request.setName("Emrah");
        request.setAge(32);
        request.setGender("MALE");
        request.setHeight(180.0);
        request.setWeight(82.0);
        request.setBodyFat(19.2);
        request.setTargetWeight(78.0);
        request.setWeeklyWeightChangeTargetKg(0.5);
        request.setGoalType(GoalType.LOSE_WEIGHT);
        request.setActivityLevel(ActivityLevel.MODERATE);
        return request;
    }
}
