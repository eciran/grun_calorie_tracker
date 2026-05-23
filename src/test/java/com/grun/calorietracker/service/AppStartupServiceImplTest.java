package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AppStartupDto;
import com.grun.calorietracker.entity.FederatedIdentityEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;
import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.enums.AuthProvider;
import com.grun.calorietracker.enums.GoalType;
import com.grun.calorietracker.repository.FederatedIdentityRepository;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.service.impl.AppStartupServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

class AppStartupServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private FederatedIdentityRepository federatedIdentityRepository;

    private AppStartupServiceImpl appStartupService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        appStartupService = new AppStartupServiceImpl(userService, goalRepository, federatedIdentityRepository);
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

        AppStartupDto result = appStartupService.getStartupState("user@example.com");

        assertEquals(true, result.isProfileComplete());
        assertEquals(true, result.isHasActiveGoal());
        assertEquals(true, result.isOnboardingCompleted());
        assertEquals(true, result.isEmailVerified());
        assertEquals(true, result.isPasswordSet());
        assertEquals(true, result.isDashboardReady());
        assertEquals("OPEN_DASHBOARD", result.getNextStep());
        assertEquals("user@example.com", result.getProfile().getEmail());
        assertEquals(true, result.getProfile().getEmailVerified());
        assertEquals(true, result.getProfile().getPasswordSet());
        assertEquals(1, result.getLinkedIdentities().size());
        assertEquals(AuthProvider.GOOGLE, result.getLinkedIdentities().get(0).provider());
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

        AppStartupDto result = appStartupService.getStartupState("user@example.com");

        assertEquals(true, result.isProfileComplete());
        assertEquals(false, result.isHasActiveGoal());
        assertEquals(false, result.isOnboardingCompleted());
        assertEquals(false, result.isDashboardReady());
        assertEquals("COMPLETE_ONBOARDING", result.getNextStep());
        assertNull(result.getGoal());
    }

    @Test
    void getStartupState_whenEmailNotVerified_returnsVerifyEmailStep() {
        UserEntity user = completeUser();
        user.setEmailVerified(false);
        UserGoalEntity goal = goal();

        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(goalRepository.findByUser(user)).thenReturn(Optional.of(goal));
        when(federatedIdentityRepository.findByUserEmailOrderByCreatedAtAsc("user@example.com"))
                .thenReturn(java.util.List.of());

        AppStartupDto result = appStartupService.getStartupState("user@example.com");

        assertEquals(true, result.isOnboardingCompleted());
        assertEquals(false, result.isEmailVerified());
        assertEquals(false, result.isDashboardReady());
        assertEquals("VERIFY_EMAIL", result.getNextStep());
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
}
