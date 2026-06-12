package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.GdprDataExportDto;
import com.grun.calorietracker.entity.SubscriptionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.SubscriptionStatus;
import com.grun.calorietracker.repository.AppliedPromoRepository;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.DeviceDataRepository;
import com.grun.calorietracker.repository.EmailVerificationTokenRepository;
import com.grun.calorietracker.repository.ExerciseLogRepository;
import com.grun.calorietracker.repository.FastingPlanRepository;
import com.grun.calorietracker.repository.FastingSessionRepository;
import com.grun.calorietracker.repository.FederatedIdentityRepository;
import com.grun.calorietracker.repository.FoodDiaryNoteRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.repository.HealthConnectionRepository;
import com.grun.calorietracker.repository.MealTemplateRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.PasswordResetTokenRepository;
import com.grun.calorietracker.repository.ProgressLogRepository;
import com.grun.calorietracker.repository.RefreshTokenRepository;
import com.grun.calorietracker.repository.StepGoalRepository;
import com.grun.calorietracker.repository.SubscriptionProviderEventRepository;
import com.grun.calorietracker.repository.SubscriptionRepository;
import com.grun.calorietracker.repository.UserConsentRepository;
import com.grun.calorietracker.repository.UserAchievementRepository;
import com.grun.calorietracker.repository.UserFavoriteRepository;
import com.grun.calorietracker.repository.UserPushTokenRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.repository.UserSubscriptionEntitlementRepository;
import com.grun.calorietracker.repository.WaterLogRepository;
import com.grun.calorietracker.repository.WaterReminderSettingsRepository;
import com.grun.calorietracker.service.impl.AccountGdprServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AccountGdprServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private FoodLogsRepository foodLogsRepository;
    @Mock private ExerciseLogRepository exerciseLogRepository;
    @Mock private ProgressLogRepository progressLogRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private FederatedIdentityRepository federatedIdentityRepository;
    @Mock private MealTemplateRepository mealTemplateRepository;
    @Mock private UserFavoriteRepository userFavoriteRepository;
    @Mock private UserAchievementRepository userAchievementRepository;
    @Mock private DeviceDataRepository deviceDataRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private AccountIdentityService accountIdentityService;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private FoodDiaryNoteRepository foodDiaryNoteRepository;
    @Mock private HealthConnectionRepository healthConnectionRepository;
    @Mock private AppliedPromoRepository appliedPromoRepository;
    @Mock private SubscriptionProviderEventRepository subscriptionProviderEventRepository;
    @Mock private UserSubscriptionEntitlementRepository userSubscriptionEntitlementRepository;
    @Mock private FoodItemRepository foodItemRepository;
    @Mock private UserConsentRepository userConsentRepository;
    @Mock private AiRequestHistoryRepository aiRequestHistoryRepository;
    @Mock private WaterLogRepository waterLogRepository;
    @Mock private WaterReminderSettingsRepository waterReminderSettingsRepository;
    @Mock private FastingPlanRepository fastingPlanRepository;
    @Mock private FastingSessionRepository fastingSessionRepository;
    @Mock private StepGoalRepository stepGoalRepository;
    @Mock private UserPushTokenRepository userPushTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private AccountGdprServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AccountGdprServiceImpl(
                userRepository,
                foodLogsRepository,
                exerciseLogRepository,
                progressLogRepository,
                notificationRepository,
                federatedIdentityRepository,
                mealTemplateRepository,
                userFavoriteRepository,
                userAchievementRepository,
                deviceDataRepository,
                subscriptionRepository,
                accountIdentityService,
                refreshTokenRepository,
                passwordResetTokenRepository,
                emailVerificationTokenRepository,
                goalRepository,
                foodDiaryNoteRepository,
                healthConnectionRepository,
                appliedPromoRepository,
                subscriptionProviderEventRepository,
                userSubscriptionEntitlementRepository,
                foodItemRepository,
                userConsentRepository,
                aiRequestHistoryRepository,
                waterLogRepository,
                waterReminderSettingsRepository,
                fastingPlanRepository,
                fastingSessionRepository,
                stepGoalRepository,
                userPushTokenRepository,
                passwordEncoder
        );

        user = new UserEntity();
        user.setId(10L);
        user.setEmail("user@grun.app");
        user.setName("User");
        user.setPassword("encoded-current");
        user.setPreferredLanguage(PreferredLanguage.EN);
        user.setEmailVerified(true);
        user.setPasswordSet(true);
    }

    @Test
    void exportMyData_returnsCountsAndSubscriptionSnapshot() {
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setAiMonthlyQuota(100);

        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(subscription));
        when(foodLogsRepository.countByUser(user)).thenReturn(11L);
        when(exerciseLogRepository.countByUser(user)).thenReturn(7L);
        when(progressLogRepository.countByUser(user)).thenReturn(3L);
        when(notificationRepository.countByUser(user)).thenReturn(2L);
        when(federatedIdentityRepository.countByUser(user)).thenReturn(1L);
        when(mealTemplateRepository.countByUser(user)).thenReturn(4L);
        when(userFavoriteRepository.countByUser(user)).thenReturn(5L);
        when(deviceDataRepository.countByUser(user)).thenReturn(8L);
        when(userConsentRepository.countByUser(user)).thenReturn(2L);
        when(aiRequestHistoryRepository.countByUser(user)).thenReturn(6L);
        when(waterLogRepository.countByUser(user)).thenReturn(9L);
        when(fastingSessionRepository.countByUser(user)).thenReturn(3L);

        GdprDataExportDto dto = service.exportMyData("user@grun.app");

        assertEquals("user@grun.app", dto.getEmail());
        assertEquals(11L, dto.getFoodLogCount());
        assertEquals(2L, dto.getConsentCount());
        assertEquals(6L, dto.getAiRequestCount());
        assertEquals(9L, dto.getWaterLogCount());
        assertEquals(3L, dto.getFastingSessionCount());
        assertEquals(100, dto.getSubscription().getAiMonthlyQuota());
        assertEquals(0, dto.getFoodLogs().size());
    }

    @Test
    void anonymizeAndDeleteAccount_deletesUserLinkedDataAndScrubsProviderEvents() {
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("CurrentPass1!", "encoded-current")).thenReturn(true);
        when(passwordEncoder.encode(any())).thenReturn("encoded");

        service.anonymizeAndDeleteAccount("user@grun.app", "DELETE_MY_ACCOUNT", "CurrentPass1!");

        verify(refreshTokenRepository).deleteByUser(user);
        verify(foodLogsRepository).deleteByUser(user);
        verify(waterLogRepository).deleteByUser(user);
        verify(waterReminderSettingsRepository).deleteByUser(user);
        verify(fastingSessionRepository).deleteByUser(user);
        verify(fastingPlanRepository).deleteByUser(user);
        verify(stepGoalRepository).deleteByUser(user);
        verify(userPushTokenRepository).deleteByUser(user);
        verify(subscriptionProviderEventRepository).anonymizeUserReferences(user, "deleted-user:10", "{}");
        verify(aiRequestHistoryRepository).deleteByUser(user);
        verify(subscriptionRepository).deleteByUser(user);
        verify(userRepository).save(user);
    }

    @Test
    void anonymizeAndDeleteAccount_rejectsInvalidCurrentPassword() {
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-current")).thenReturn(false);

        assertThrows(
                com.grun.calorietracker.exception.InvalidCredentialsException.class,
                () -> service.anonymizeAndDeleteAccount("user@grun.app", "DELETE_MY_ACCOUNT", "wrong")
        );

        verify(refreshTokenRepository, never()).deleteByUser(user);
        verify(userRepository, never()).save(user);
    }
}
