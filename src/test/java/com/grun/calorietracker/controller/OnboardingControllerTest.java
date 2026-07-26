package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.dto.OnboardingCompleteRequestDto;
import com.grun.calorietracker.dto.MyProfileDto;
import com.grun.calorietracker.dto.OnboardingCompleteResponseDto;
import com.grun.calorietracker.dto.ProfileBodyDto;
import com.grun.calorietracker.dto.ProfilePreferencesDto;
import com.grun.calorietracker.dto.ProfileSecurityDto;
import com.grun.calorietracker.dto.OnboardingGoalStepDto;
import com.grun.calorietracker.dto.OnboardingPreviewResponseDto;
import com.grun.calorietracker.dto.OnboardingStateDto;
import com.grun.calorietracker.dto.OnboardingStepUpdateRequestDto;
import com.grun.calorietracker.dto.UserGoalDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.enums.GoalType;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.OnboardingStatus;
import com.grun.calorietracker.enums.OnboardingStep;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.service.OnboardingAnalyticsService;
import com.grun.calorietracker.service.OnboardingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OnboardingControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private OnboardingService onboardingService;
    @MockitoBean private OnboardingAnalyticsService onboardingAnalyticsService;

    @Test
    @WithMockUser(username = "user@example.com")
    void getState_returnsResumableProgress() throws Exception {
        when(onboardingService.getState("user@example.com")).thenReturn(state(OnboardingStep.GOAL, false));

        mockMvc.perform(get("/api/v1/onboarding/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.currentStep").value("GOAL"))
                .andExpect(jsonPath("$.completedSteps.length()").value(2))
                .andExpect(jsonPath("$.canComplete").value(false));
        org.mockito.Mockito.verify(onboardingAnalyticsService).recordServerEvent(
                "user@example.com",
                com.grun.calorietracker.enums.ProductAnalyticsEventType.ONBOARDING_RESUMED,
                OnboardingStep.GOAL
        );
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void updateStep_whenServiceFails_recordsFailureAndReturnsBadRequest() throws Exception {
        OnboardingStepUpdateRequestDto request = new OnboardingStepUpdateRequestDto();
        request.setGoal(new OnboardingGoalStepDto(78.0, 0.5, GoalType.LOSE_WEIGHT, ActivityLevel.MODERATE));
        when(onboardingService.updateStep(eq(OnboardingStep.GOAL), any(), eq("user@example.com")))
                .thenThrow(new IllegalArgumentException("unsafe goal"));

        mockMvc.perform(patch("/api/v1/onboarding/steps/GOAL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verify(onboardingAnalyticsService).recordServerEvent(
                "user@example.com",
                com.grun.calorietracker.enums.ProductAnalyticsEventType.ONBOARDING_STEP_FAILED,
                OnboardingStep.GOAL
        );
    }
    @Test
    @WithMockUser(username = "user@example.com")
    void updateStep_savesMatchingPayload() throws Exception {
        OnboardingStepUpdateRequestDto request = new OnboardingStepUpdateRequestDto();
        request.setGoal(new OnboardingGoalStepDto(78.0, 0.5, GoalType.LOSE_WEIGHT, ActivityLevel.MODERATE));
        when(onboardingService.updateStep(eq(OnboardingStep.GOAL), any(), eq("user@example.com")))
                .thenReturn(state(OnboardingStep.REVIEW, true));

        mockMvc.perform(patch("/api/v1/onboarding/steps/GOAL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStep").value("REVIEW"))
                .andExpect(jsonPath("$.canComplete").value(true));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void preview_returnsCalculationWithoutCompletion() throws Exception {
        GoalCalculationResponse calculation =
                new GoalCalculationResponse(2209, 138, 74, 248);
        calculation.setEstimatedDurationWeeks(8);
        when(onboardingService.preview("user@example.com")).thenReturn(
                new OnboardingPreviewResponseDto(
                        calculation,
                        state(OnboardingStep.REVIEW, true)
                )
        );

        mockMvc.perform(post("/api/v1/onboarding/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculation.calculatedCalorieNeed").value(2209))
                .andExpect(jsonPath("$.calculation.estimatedDurationWeeks").value(8))
                .andExpect(jsonPath("$.state.currentStep").value("REVIEW"));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void completeOnboarding_withoutBody_completesSavedDraft() throws Exception {
        when(onboardingService.completeOnboarding("user@example.com")).thenReturn(completedResponse());

        mockMvc.perform(post("/api/v1/onboarding/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.calculation.calculatedCalorieNeed").value(2209));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void recordClientEvent_acceptsPrivacySafeViewedEvent() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "STEP_VIEWED",
                                  "step": "NUTRITION",
                                  "language": "en_IE",
                                  "durationMs": 1200
                                }
                                """))
                .andExpect(status().isAccepted());

        org.mockito.Mockito.verify(onboardingAnalyticsService)
                .recordClientEvent(eq("user@example.com"), any());
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void recordClientEvent_rejectsViewedEventWithoutStep() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"STEP_VIEWED","language":"en"}
                                """))
                .andExpect(status().isBadRequest());
    }
    @Test
    @WithMockUser(username = "user@example.com")
    void completeOnboarding_whenLegacyRequestIsValid_returnsProfileGoalAndCalculation() throws Exception {
        OnboardingCompleteRequestDto request = validRequest();
        when(onboardingService.completeOnboarding(any(OnboardingCompleteRequestDto.class), eq("user@example.com")))
                .thenReturn(completedResponse());

        mockMvc.perform(post("/api/v1/onboarding/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.profile.name").value("Emrah"))
                .andExpect(jsonPath("$.profile.preferences.marketRegion").value("UK_IE"))
                .andExpect(jsonPath("$.profile.preferences.preferredLanguage").value("EN"))
                .andExpect(jsonPath("$.calculation.calculatedCalorieNeed").value(2209));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void completeOnboarding_whenRequiredFieldsAreMissing_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/complete")
                        .header("Accept-Language", "tr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OnboardingCompleteRequestDto())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Dogrulama hatasi"));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void updateStep_whenStepIsUnknown_returnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/onboarding/steps/UNKNOWN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    @Test
    void onboardingEndpoints_whenUnauthenticated_returnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/onboarding/state"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/onboarding/preview"))
                .andExpect(status().isUnauthorized());
    }

    private OnboardingStateDto state(OnboardingStep currentStep, boolean canComplete) {
        List<OnboardingStep> completed = canComplete
                ? List.of(OnboardingStep.PROFILE, OnboardingStep.PREFERENCES, OnboardingStep.GOAL)
                : List.of(OnboardingStep.PROFILE, OnboardingStep.PREFERENCES);
        return new OnboardingStateDto(
                OnboardingStatus.IN_PROGRESS,
                currentStep,
                completed,
                canComplete,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private OnboardingCompleteResponseDto completedResponse() {
        MyProfileDto profile = MyProfileDto.builder()
                .email("user@example.com")
                .name("Emrah")
                .body(ProfileBodyDto.builder()
                        .age(32).gender("MALE").height(180.0).weight(82.0).bodyFat(19.2)
                        .build())
                .preferences(ProfilePreferencesDto.builder()
                        .marketRegion(MarketRegion.UK_IE).preferredLanguage(PreferredLanguage.EN)
                        .build())
                .security(ProfileSecurityDto.builder().emailVerified(true).passwordSet(true).build())
                .build();
        UserGoalDto goal = new UserGoalDto();
        goal.setDailyCalorieGoal(2209);
        goal.setDailyProteinGoal(138.0);
        goal.setDailyFatGoal(74.0);
        goal.setDailyCarbGoal(248.0);
        return new OnboardingCompleteResponseDto(
                profile,
                goal,
                new GoalCalculationResponse(2209, 138, 74, 248),
                true
        );
    }

    private OnboardingCompleteRequestDto validRequest() {
        OnboardingCompleteRequestDto request = new OnboardingCompleteRequestDto();
        request.setName("Emrah");
        request.setAge(32);
        request.setGender("MALE");
        request.setHeight(180.0);
        request.setWeight(82.0);
        request.setBodyFat(19.2);
        request.setMarketRegion(MarketRegion.UK_IE);
        request.setPreferredLanguage(PreferredLanguage.EN);
        request.setTimeZone("Europe/Dublin");
        request.setTargetWeight(78.0);
        request.setWeeklyWeightChangeTargetKg(0.5);
        request.setGoalType(GoalType.LOSE_WEIGHT);
        request.setActivityLevel(ActivityLevel.MODERATE);
        return request;
    }
}