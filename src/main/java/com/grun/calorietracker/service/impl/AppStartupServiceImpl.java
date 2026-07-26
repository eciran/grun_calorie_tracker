package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AppStartupDto;
import com.grun.calorietracker.dto.LinkedIdentityDto;
import com.grun.calorietracker.dto.OnboardingStateDto;
import com.grun.calorietracker.dto.UserGoalDto;
import com.grun.calorietracker.entity.FederatedIdentityEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;
import com.grun.calorietracker.enums.OnboardingStatus;
import com.grun.calorietracker.enums.ProductAnalyticsEventType;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.mapper.UserGoalMapper;
import com.grun.calorietracker.repository.FederatedIdentityRepository;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.service.AppStartupService;
import com.grun.calorietracker.service.HealthIntegrationService;
import com.grun.calorietracker.service.OnboardingAnalyticsService;
import com.grun.calorietracker.service.OnboardingService;
import com.grun.calorietracker.service.SubscriptionService;
import com.grun.calorietracker.service.UserService;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppStartupServiceImpl implements AppStartupService {

    private static final String NEXT_STEP_COMPLETE_ONBOARDING = "COMPLETE_ONBOARDING";
    private static final String NEXT_STEP_OPEN_DASHBOARD = "OPEN_DASHBOARD";

    private final UserService userService;
    private final GoalRepository goalRepository;
    private final FederatedIdentityRepository federatedIdentityRepository;
    private final SubscriptionService subscriptionService;
    private final HealthIntegrationService healthIntegrationService;
    private final UserTimeZoneSupport userTimeZoneSupport;
    private final OnboardingService onboardingService;
    private final OnboardingAnalyticsService onboardingAnalyticsService;

    @Override
    @Transactional(readOnly = true)
    public AppStartupDto getStartupState(String email) {
        UserEntity user = userService.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));

        Optional<UserGoalEntity> goalOpt = goalRepository.findByUser(user);
        boolean profileComplete = isProfileComplete(user);
        boolean hasActiveGoal = goalOpt.isPresent();
        boolean onboardingCompleted = profileComplete && hasActiveGoal;
        boolean emailVerified = Boolean.TRUE.equals(user.getEmailVerified());
        boolean passwordSet = Boolean.TRUE.equals(user.getPasswordSet());
        boolean dashboardReady = onboardingCompleted;
        boolean healthAccessAllowed = subscriptionService.hasFeatureAccess(email, SubscriptionFeature.HEALTH_INTEGRATION);
        OnboardingStateDto onboardingState = onboardingService.getState(email);
        recordOnboardingEntrySafely(email, onboardingState);

        return AppStartupDto.builder()
                .profile(userService.getMyProfile(email))
                .goal(goalOpt.map(UserGoalMapper::toDto).orElse(null))
                .profileComplete(profileComplete)
                .hasActiveGoal(hasActiveGoal)
                .onboardingCompleted(onboardingCompleted)
                .onboardingState(onboardingState)
                .emailVerified(emailVerified)
                .passwordSet(passwordSet)
                .linkedIdentities(linkedIdentities(email))
                .subscription(subscriptionService.getCurrentSubscription(email))
                .healthSummary(healthAccessAllowed ? healthIntegrationService.getDailySummary(email, userTimeZoneSupport.today(user)) : null)
                .dashboardReady(dashboardReady)
                .nextStep(resolveNextStep(onboardingCompleted))
                .build();
    }

    private void recordOnboardingEntrySafely(String email, OnboardingStateDto onboardingState) {
        if (onboardingState == null || onboardingState.getStatus() != OnboardingStatus.IN_PROGRESS) {
            return;
        }

        ProductAnalyticsEventType eventType = onboardingState.getCompletedSteps() == null
                || onboardingState.getCompletedSteps().isEmpty()
                ? ProductAnalyticsEventType.ONBOARDING_STARTED
                : ProductAnalyticsEventType.ONBOARDING_RESUMED;
        try {
            onboardingAnalyticsService.recordServerEvent(email, eventType, onboardingState.getCurrentStep());
        } catch (RuntimeException exception) {
            log.warn("Onboarding startup analytics could not be recorded eventType={}", eventType, exception);
        }
    }

    private boolean isProfileComplete(UserEntity user) {
        return user.getAge() != null
                && user.getGender() != null
                && user.getHeight() != null
                && user.getWeight() != null
                && user.getMarketRegion() != null
                && user.getPreferredLanguage() != null
                && user.getTimeZone() != null;
    }

    private String resolveNextStep(boolean onboardingCompleted) {
        if (!onboardingCompleted) {
            return NEXT_STEP_COMPLETE_ONBOARDING;
        }
        return NEXT_STEP_OPEN_DASHBOARD;
    }

    private java.util.List<LinkedIdentityDto> linkedIdentities(String email) {
        return federatedIdentityRepository.findByUserEmailOrderByCreatedAtAsc(email)
                .stream()
                .map(this::toLinkedIdentityDto)
                .toList();
    }

    private LinkedIdentityDto toLinkedIdentityDto(FederatedIdentityEntity identity) {
        return new LinkedIdentityDto(
                identity.getProvider(),
                identity.getProviderEmail(),
                identity.getCreatedAt()
        );
    }
}
