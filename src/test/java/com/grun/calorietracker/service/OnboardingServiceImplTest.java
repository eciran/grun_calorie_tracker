package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.GoalCalculationRequestDto;
import com.grun.calorietracker.dto.OnboardingCompleteRequestDto;
import com.grun.calorietracker.dto.UserGoalDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.enums.GoalType;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.service.impl.OnboardingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private UserGoalService userGoalService;

    private OnboardingServiceImpl onboardingService;

    @BeforeEach
    void setUp() {
        onboardingService = new OnboardingServiceImpl(userService, userGoalService);
    }

    @Test
    void completeOnboarding_updatesProfileAndSavesCalculatedGoal() {
        OnboardingCompleteRequestDto request = validRequest();
        UserProfileDto profile = UserProfileDto.builder()
                .name("Emrah")
                .age(32)
                .gender("MALE")
                .height(180.0)
                .weight(82.0)
                .bodyFat(19.2)
                .marketRegion(MarketRegion.UK)
                .build();
        UserGoalDto savedGoal = new UserGoalDto();
        savedGoal.setDailyCalorieGoal(2209);
        savedGoal.setDailyProteinGoal(138.0);
        savedGoal.setDailyFatGoal(74.0);
        savedGoal.setDailyCarbGoal(248.0);

        when(userService.updateCurrentUser(any(UserProfileDto.class), eq("user@example.com"))).thenReturn(profile);
        when(userGoalService.saveUserGoal(any(GoalCalculationRequestDto.class), eq("user@example.com"))).thenReturn(savedGoal);

        var response = onboardingService.completeOnboarding(request, "user@example.com");

        assertTrue(response.isOnboardingCompleted());
        assertEquals("Emrah", response.getProfile().getName());
        assertEquals(false, response.getProfile().getGoalRecalculationRecommended());
        assertEquals(2209, response.getCalculation().getCalculatedCalorieNeed());

        ArgumentCaptor<UserProfileDto> profileCaptor = ArgumentCaptor.forClass(UserProfileDto.class);
        ArgumentCaptor<GoalCalculationRequestDto> goalCaptor = ArgumentCaptor.forClass(GoalCalculationRequestDto.class);
        org.mockito.Mockito.verify(userService).updateCurrentUser(profileCaptor.capture(), eq("user@example.com"));
        org.mockito.Mockito.verify(userGoalService).saveUserGoal(goalCaptor.capture(), eq("user@example.com"));
        assertEquals(19.2, profileCaptor.getValue().getBodyFat());
        assertEquals(MarketRegion.UK, profileCaptor.getValue().getMarketRegion());
        assertEquals(GoalType.LOSE_WEIGHT, goalCaptor.getValue().getGoalType());
    }

    private OnboardingCompleteRequestDto validRequest() {
        OnboardingCompleteRequestDto request = new OnboardingCompleteRequestDto();
        request.setName("Emrah");
        request.setAge(32);
        request.setGender("MALE");
        request.setHeight(180.0);
        request.setWeight(82.0);
        request.setBodyFat(19.2);
        request.setMarketRegion(MarketRegion.UK);
        request.setTargetWeight(78.0);
        request.setWeeklyWeightChangeTargetKg(0.5);
        request.setGoalType(GoalType.LOSE_WEIGHT);
        request.setActivityLevel(ActivityLevel.MODERATE);
        return request;
    }
}
