package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AppStartupDto;
import com.grun.calorietracker.dto.OnboardingStateDto;
import com.grun.calorietracker.entity.FederatedIdentityEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;
import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.enums.AuthProvider;
import com.grun.calorietracker.enums.BillingPeriod;
import com.grun.calorietracker.enums.GoalType;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.OnboardingStatus;
import com.grun.calorietracker.enums.OnboardingStep;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.ProductAnalyticsEventType;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionStatus;
import com.grun.calorietracker.repository.FederatedIdentityRepository;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.service.impl.AppStartupServiceImpl;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppStartupServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private FederatedIdentityRepository federatedIdentityRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private HealthIntegrationService healthIntegrationService;

    @Mock
    private OnboardingService onboardingService;

    @Mock
    private OnboardingAnalyticsService onboardingAnalyticsService;

    @Mock
    private UserActivityService userActivityService;

    private AppStartupServiceImpl appStartupService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        appStartupService = new AppStartupServiceImpl(
                userService,
                goalRepository,
                federatedIdentityRepository,
                subscriptionService,
                healthIntegrationService,
                new UserTimeZoneSupport(),
                onboardingService,
                onboardingAnalyticsService,
                userActivityService
        );
        when(onboardingService.getState("user@example.com")).thenReturn(onboardingState());
        when(userService.getMyProfile("user@example.com")).thenReturn(myProfile());
    }

    @Test
    void getStartupState_whenProfileAndGoalComplete_returnsDashboardReady() {
        UserEntity user = completeUser();
        user.setEmailVerified(true);
        UserGoalEntity goal = goal();

        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(goalRepository.findByUser(user)).thenReturn(Optional.of(goal));
        when(federatedIdentityRepository.findByUserEmailOrderByCreatedAtAsc("user@example.com"))
                .thenReturn(java.util.List.of(identity(user, AuthProvider.GOOGLE)));
        when(subscriptionService.getCurrentSubscription("user@example.com")).thenReturn(subscription());
        when(subscriptionService.hasFeatureAccess("user@example.com", com.grun.calorietracker.enums.SubscriptionFeature.HEALTH_INTEGRATION))
                .thenReturn(true);
        when(healthIntegrationService.getDailySummary(org.mockito.ArgumentMatchers.eq("user@example.com"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(healthSummary());

        AppStartupDto result = appStartupService.getStartupState("user@example.com");

        assertEquals(true, result.isProfileComplete());
        assertEquals(true, result.isHasActiveGoal());
        assertEquals(true, result.isOnboardingCompleted());
        assertEquals(true, result.isEmailVerified());
        assertEquals(true, result.isPasswordSet());
        assertEquals(true, result.isDashboardReady());
        assertEquals("OPEN_DASHBOARD", result.getNextStep());
        assertEquals(com.grun.calorietracker.enums.OnboardingStep.COMPLETE, result.getOnboardingState().getCurrentStep());
        assertEquals("user@example.com", result.getProfile().getEmail());
        assertEquals(true, result.getProfile().getSecurity().getEmailVerified());
        assertEquals(true, result.getProfile().getSecurity().getPasswordSet());
        assertEquals(MarketRegion.UK_IE, result.getProfile().getPreferences().getMarketRegion());
        assertEquals(PreferredLanguage.EN, result.getProfile().getPreferences().getPreferredLanguage());
        assertEquals("Europe/Dublin", result.getProfile().getPreferences().getTimeZone());
        assertEquals(1, result.getLinkedIdentities().size());
        assertEquals(AuthProvider.GOOGLE, result.getLinkedIdentities().get(0).provider());
        assertEquals(SubscriptionPlan.PLUS, result.getSubscription().getPlanType());
        assertEquals(10, result.getSubscription().getAiRemainingThisPeriod());
        assertEquals(false, result.getHealthSummary().getHasHealthData());
        assertNotNull(result.getProfile());
        assertNotNull(result.getGoal());
        assertEquals(2242, result.getGoal().getDailyCalorieGoal());
    }

    @Test
    void getStartupState_whenGoalMissing_returnsCompleteOnboardingStep() {
        UserEntity user = completeUser();
        user.setEmailVerified(true);

        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(goalRepository.findByUser(user)).thenReturn(Optional.empty());
        when(federatedIdentityRepository.findByUserEmailOrderByCreatedAtAsc("user@example.com"))
                .thenReturn(java.util.List.of());
        when(subscriptionService.getCurrentSubscription("user@example.com")).thenReturn(subscription());
        when(subscriptionService.hasFeatureAccess("user@example.com", com.grun.calorietracker.enums.SubscriptionFeature.HEALTH_INTEGRATION))
                .thenReturn(true);
        when(healthIntegrationService.getDailySummary(org.mockito.ArgumentMatchers.eq("user@example.com"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(healthSummary());

        AppStartupDto result = appStartupService.getStartupState("user@example.com");

        assertEquals(true, result.isProfileComplete());
        assertEquals(false, result.isHasActiveGoal());
        assertEquals(false, result.isOnboardingCompleted());
        assertEquals(false, result.isDashboardReady());
        assertEquals("COMPLETE_ONBOARDING", result.getNextStep());
        assertNull(result.getGoal());
    }

    @Test
    void getStartupState_whenEmailNotVerified_keepsDashboardOpenForReminderFlow() {
        UserEntity user = completeUser();
        user.setEmailVerified(false);
        UserGoalEntity goal = goal();

        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(goalRepository.findByUser(user)).thenReturn(Optional.of(goal));
        when(federatedIdentityRepository.findByUserEmailOrderByCreatedAtAsc("user@example.com"))
                .thenReturn(java.util.List.of());
        when(subscriptionService.getCurrentSubscription("user@example.com")).thenReturn(subscription());
        when(subscriptionService.hasFeatureAccess("user@example.com", com.grun.calorietracker.enums.SubscriptionFeature.HEALTH_INTEGRATION))
                .thenReturn(true);
        when(healthIntegrationService.getDailySummary(org.mockito.ArgumentMatchers.eq("user@example.com"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(healthSummary());

        AppStartupDto result = appStartupService.getStartupState("user@example.com");

        assertEquals(true, result.isOnboardingCompleted());
        assertEquals(false, result.isEmailVerified());
        assertEquals(true, result.isDashboardReady());
        assertEquals("OPEN_DASHBOARD", result.getNextStep());
        assertEquals(com.grun.calorietracker.enums.OnboardingStep.COMPLETE, result.getOnboardingState().getCurrentStep());
    }

    @Test
    void getStartupState_whenOnboardingIsInProgress_recordsResumeAnalytics() {
        UserEntity user = completeUser();
        OnboardingStateDto state = inProgressOnboardingState();

        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(goalRepository.findByUser(user)).thenReturn(Optional.empty());
        when(federatedIdentityRepository.findByUserEmailOrderByCreatedAtAsc("user@example.com"))
                .thenReturn(java.util.List.of());
        when(subscriptionService.getCurrentSubscription("user@example.com")).thenReturn(subscription());
        when(subscriptionService.hasFeatureAccess("user@example.com", com.grun.calorietracker.enums.SubscriptionFeature.HEALTH_INTEGRATION))
                .thenReturn(false);
        when(onboardingService.getState("user@example.com")).thenReturn(state);

        appStartupService.getStartupState("user@example.com");

        verify(onboardingAnalyticsService).recordServerEvent(
                "user@example.com",
                ProductAnalyticsEventType.ONBOARDING_RESUMED,
                OnboardingStep.GOAL
        );
    }

    @Test
    void getStartupState_whenAnalyticsFails_stillReturnsStartupState() {
        UserEntity user = completeUser();
        OnboardingStateDto state = inProgressOnboardingState();

        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(goalRepository.findByUser(user)).thenReturn(Optional.empty());
        when(federatedIdentityRepository.findByUserEmailOrderByCreatedAtAsc("user@example.com"))
                .thenReturn(java.util.List.of());
        when(subscriptionService.getCurrentSubscription("user@example.com")).thenReturn(subscription());
        when(subscriptionService.hasFeatureAccess("user@example.com", com.grun.calorietracker.enums.SubscriptionFeature.HEALTH_INTEGRATION))
                .thenReturn(false);
        when(onboardingService.getState("user@example.com")).thenReturn(state);
        doThrow(new IllegalStateException("analytics unavailable"))
                .when(onboardingAnalyticsService)
                .recordServerEvent("user@example.com", ProductAnalyticsEventType.ONBOARDING_RESUMED, OnboardingStep.GOAL);

        AppStartupDto result = appStartupService.getStartupState("user@example.com");

        assertNotNull(result);
        assertEquals(OnboardingStep.GOAL, result.getOnboardingState().getCurrentStep());
    }

    private com.grun.calorietracker.dto.MyProfileDto myProfile() {
        return com.grun.calorietracker.dto.MyProfileDto.builder()
                .id(1L)
                .email("user@example.com")
                .name("Demo User")
                .body(com.grun.calorietracker.dto.ProfileBodyDto.builder()
                        .age(32).gender("MALE").height(180.0).weight(82.0).bmi(25.3).bodyFat(19.2)
                        .build())
                .preferences(com.grun.calorietracker.dto.ProfilePreferencesDto.builder()
                        .marketRegion(MarketRegion.UK_IE)
                        .preferredLanguage(PreferredLanguage.EN)
                        .timeZone("Europe/Dublin")
                        .build())
                .security(com.grun.calorietracker.dto.ProfileSecurityDto.builder()
                        .emailVerified(true).passwordSet(true)
                        .build())
                .goalRecalculationRecommended(false)
                .build();
    }
    private UserEntity completeUser() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setName("Demo User");
        user.setAge(32);
        user.setGender("MALE");
        user.setHeight(180.0);
        user.setWeight(82.0);
        user.setBodyFatPercentage(19.2);
        user.setBmi(25.3);
        user.setEmailVerified(true);
        user.setPasswordSet(true);
        user.setMarketRegion(MarketRegion.UK_IE);
        user.setPreferredLanguage(PreferredLanguage.EN);
        return user;
    }

    private FederatedIdentityEntity identity(UserEntity user, AuthProvider provider) {
        FederatedIdentityEntity identity = new FederatedIdentityEntity();
        identity.setUser(user);
        identity.setProvider(provider);
        identity.setProviderEmail("user@example.com");
        identity.setProviderSubject("provider-sub");
        identity.setCreatedAt(java.time.LocalDateTime.now());
        return identity;
    }

    private UserGoalEntity goal() {
        UserGoalEntity goal = new UserGoalEntity();
        goal.setId(10L);
        goal.setTargetWeight(78.0);
        goal.setDailyCalorieGoal(2242);
        goal.setDailyProteinGoal(168.0);
        goal.setDailyFatGoal(62.0);
        goal.setDailyCarbGoal(252.0);
        goal.setGoalType(GoalType.LOSE_WEIGHT);
        goal.setActivityLevel(ActivityLevel.MODERATE);
        return goal;
    }

    private OnboardingStateDto onboardingState() {
        return new OnboardingStateDto(
                com.grun.calorietracker.enums.OnboardingStatus.COMPLETED,
                com.grun.calorietracker.enums.OnboardingStep.COMPLETE,
                java.util.List.of(
                        com.grun.calorietracker.enums.OnboardingStep.PROFILE,
                        com.grun.calorietracker.enums.OnboardingStep.PREFERENCES,
                        com.grun.calorietracker.enums.OnboardingStep.GOAL
                ),
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                java.time.LocalDateTime.now()
        );
    }
    private OnboardingStateDto inProgressOnboardingState() {
        return new OnboardingStateDto(
                OnboardingStatus.IN_PROGRESS,
                OnboardingStep.GOAL,
                java.util.List.of(OnboardingStep.PROFILE, OnboardingStep.PREFERENCES),
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                java.time.LocalDateTime.now()
        );
    }

    private com.grun.calorietracker.dto.SubscriptionDto subscription() {
        com.grun.calorietracker.dto.SubscriptionDto subscription = new com.grun.calorietracker.dto.SubscriptionDto();
        subscription.setPlanType(SubscriptionPlan.PLUS);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setBillingPeriod(BillingPeriod.MONTHLY);
        subscription.setAiMonthlyQuota(15);
        subscription.setAiUsedThisPeriod(5);
        subscription.setAiRemainingThisPeriod(10);
        subscription.setAutoRenew(true);
        return subscription;
    }

    private com.grun.calorietracker.dto.HealthDailySummaryDto healthSummary() {
        com.grun.calorietracker.dto.HealthDailySummaryDto summary = new com.grun.calorietracker.dto.HealthDailySummaryDto();
        summary.setHasHealthData(false);
        return summary;
    }
}
