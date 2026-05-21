package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.dto.OnboardingCompleteRequestDto;
import com.grun.calorietracker.dto.OnboardingCompleteResponseDto;
import com.grun.calorietracker.dto.UserGoalDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.service.OnboardingService;
import com.grun.calorietracker.service.UserGoalService;
import com.grun.calorietracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OnboardingServiceImpl implements OnboardingService {

    private final UserService userService;
    private final UserGoalService userGoalService;

    @Override
    @Transactional
    public OnboardingCompleteResponseDto completeOnboarding(OnboardingCompleteRequestDto request, String email) {
        UserProfileDto profile = userService.updateCurrentUser(request.toUserProfileDto(), email);
        profile.setGoalRecalculationRecommended(false);
        profile.setGoalRecalculationReason(null);
        UserGoalDto goal = userGoalService.saveUserGoal(request.toGoalCalculationRequestDto(), email);
        GoalCalculationResponse calculation = new GoalCalculationResponse(
                goal.getDailyCalorieGoal(),
                goal.getDailyProteinGoal().intValue(),
                goal.getDailyFatGoal().intValue(),
                goal.getDailyCarbGoal().intValue()
        );

        return new OnboardingCompleteResponseDto(profile, goal, calculation, true);
    }
}
