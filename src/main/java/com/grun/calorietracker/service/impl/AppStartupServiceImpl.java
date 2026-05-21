package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AppStartupDto;
import com.grun.calorietracker.dto.UserGoalDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.mapper.UserGoalMapper;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.service.AppStartupService;
import com.grun.calorietracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AppStartupServiceImpl implements AppStartupService {

    private static final String NEXT_STEP_VERIFY_EMAIL = "VERIFY_EMAIL";
    private static final String NEXT_STEP_COMPLETE_ONBOARDING = "COMPLETE_ONBOARDING";
    private static final String NEXT_STEP_OPEN_DASHBOARD = "OPEN_DASHBOARD";

    private final UserService userService;
    private final GoalRepository goalRepository;

    @Override
    public AppStartupDto getStartupState(String email) {
        UserEntity user = userService.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));

        Optional<UserGoalEntity> goalOpt = goalRepository.findByUser(user);
        boolean profileComplete = isProfileComplete(user);
        boolean hasActiveGoal = goalOpt.isPresent();
        boolean onboardingCompleted = profileComplete && hasActiveGoal;
        boolean emailVerified = Boolean.TRUE.equals(user.getEmailVerified());
        boolean dashboardReady = emailVerified && onboardingCompleted;

        return AppStartupDto.builder()
                .profile(toProfileDto(user))
                .goal(goalOpt.map(UserGoalMapper::toDto).orElse(null))
                .profileComplete(profileComplete)
                .hasActiveGoal(hasActiveGoal)
                .onboardingCompleted(onboardingCompleted)
                .emailVerified(emailVerified)
                .dashboardReady(dashboardReady)
                .nextStep(resolveNextStep(emailVerified, onboardingCompleted))
                .build();
    }

    private boolean isProfileComplete(UserEntity user) {
        return user.getAge() != null
                && user.getGender() != null
                && user.getHeight() != null
                && user.getWeight() != null;
    }

    private String resolveNextStep(boolean emailVerified, boolean onboardingCompleted) {
        if (!emailVerified) {
            return NEXT_STEP_VERIFY_EMAIL;
        }
        if (!onboardingCompleted) {
            return NEXT_STEP_COMPLETE_ONBOARDING;
        }
        return NEXT_STEP_OPEN_DASHBOARD;
    }

    private UserProfileDto toProfileDto(UserEntity user) {
        UserProfileDto profile = UserProfileDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .age(user.getAge())
                .gender(user.getGender())
                .height(user.getHeight())
                .weight(user.getWeight())
                .bmi(user.getBmi())
                .bodyFat(user.getBodyFatPercentage())
                .goalRecalculationRecommended(false)
                .build();
        profile.setGoalRecalculationReason(null);
        return profile;
    }
}
