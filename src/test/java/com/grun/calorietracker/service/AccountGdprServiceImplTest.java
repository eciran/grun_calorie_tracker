package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.GdprDataExportDto;
import com.grun.calorietracker.entity.SubscriptionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserNutritionPreferenceEntity;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.SubscriptionStatus;
import com.grun.calorietracker.enums.RecipeAllergen;
import com.grun.calorietracker.repository.AppliedPromoRepository;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.BodyMeasurementRepository;
import com.grun.calorietracker.repository.DeviceDataRepository;
import com.grun.calorietracker.repository.EmailVerificationTokenRepository;
import com.grun.calorietracker.repository.ExerciseLogRepository;
import com.grun.calorietracker.repository.FastingPlanRepository;
import com.grun.calorietracker.repository.FastingProgramRepository;
import com.grun.calorietracker.repository.AdvancedFastingReminderSettingsRepository;
import com.grun.calorietracker.repository.FastingScheduleExceptionRepository;
import com.grun.calorietracker.repository.FastingHistoryCorrectionRepository;
import com.grun.calorietracker.repository.FastingScheduleExceptionAuditRepository;
import com.grun.calorietracker.repository.FastingSessionRepository;
import com.grun.calorietracker.repository.FailedBarcodeScanRepository;
import com.grun.calorietracker.repository.FederatedIdentityRepository;
import com.grun.calorietracker.repository.FoodDiaryNoteRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.repository.HealthConnectionRepository;
import com.grun.calorietracker.repository.MealPlanRepository;
import com.grun.calorietracker.repository.MealTemplateRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.OnboardingDraftRepository;
import com.grun.calorietracker.repository.PasswordResetTokenRepository;
import com.grun.calorietracker.repository.ProductAnalyticsEventRepository;
import com.grun.calorietracker.repository.ProductCorrectionSuggestionRepository;
import com.grun.calorietracker.repository.ProgressLogRepository;
import com.grun.calorietracker.repository.RecipeLogRepository;
import com.grun.calorietracker.repository.RecipeUserInteractionRepository;
import com.grun.calorietracker.repository.RefreshTokenRepository;
import com.grun.calorietracker.repository.StepGoalRepository;
import com.grun.calorietracker.repository.SubscriptionProviderEventRepository;
import com.grun.calorietracker.repository.SubscriptionRepository;
import com.grun.calorietracker.repository.SleepGoalRepository;
import com.grun.calorietracker.repository.SleepSessionRepository;
import com.grun.calorietracker.repository.UserConsentRepository;
import com.grun.calorietracker.repository.UserAchievementRepository;
import com.grun.calorietracker.repository.UserFavoriteRepository;
import com.grun.calorietracker.repository.UserPushTokenRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.repository.UserFitnessPreferenceRepository;
import com.grun.calorietracker.repository.UserNutritionPreferenceRepository;
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
    @Mock private BodyMeasurementRepository bodyMeasurementRepository;
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
    @Mock private FastingProgramRepository fastingProgramRepository;
    @Mock private AdvancedFastingReminderSettingsRepository advancedFastingReminderSettingsRepository;
    @Mock private FastingScheduleExceptionRepository fastingScheduleExceptionRepository;
    @Mock private FastingHistoryCorrectionRepository fastingHistoryCorrectionRepository;
    @Mock private FastingScheduleExceptionAuditRepository fastingScheduleExceptionAuditRepository;
    @Mock private FastingSessionRepository fastingSessionRepository;
    @Mock private StepGoalRepository stepGoalRepository;
    @Mock private UserPushTokenRepository userPushTokenRepository;
    @Mock private MealPlanRepository mealPlanRepository;
    @Mock private RecipeLogRepository recipeLogRepository;
    @Mock private RecipeUserInteractionRepository recipeUserInteractionRepository;
    @Mock private FailedBarcodeScanRepository failedBarcodeScanRepository;
    @Mock private ProductCorrectionSuggestionRepository productCorrectionSuggestionRepository;
    @Mock private ProductAnalyticsEventRepository productAnalyticsEventRepository;
    @Mock private UserNutritionPreferenceRepository userNutritionPreferenceRepository;
    @Mock private UserFitnessPreferenceRepository userFitnessPreferenceRepository;
    @Mock private OnboardingDraftRepository onboardingDraftRepository;
    @Mock private SleepGoalRepository sleepGoalRepository;
    @Mock private SleepSessionRepository sleepSessionRepository;
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
                bodyMeasurementRepository,
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
                fastingProgramRepository,
                advancedFastingReminderSettingsRepository,
                fastingScheduleExceptionRepository,
                fastingHistoryCorrectionRepository,
                fastingScheduleExceptionAuditRepository,
                fastingSessionRepository,
                stepGoalRepository,
                userPushTokenRepository,
                mealPlanRepository,
                recipeLogRepository,
                recipeUserInteractionRepository,
                failedBarcodeScanRepository,
                productCorrectionSuggestionRepository,
                productAnalyticsEventRepository,
                userNutritionPreferenceRepository,
                userFitnessPreferenceRepository,
                onboardingDraftRepository,
                sleepGoalRepository,
                sleepSessionRepository,
                passwordEncoder
        );

        user = new UserEntity();
        user.setId(10L);
        user.setEmail("user@grun.app");
        user.setName("User");
        user.setPassword("encoded-current");
        user.setPreferredLanguage(PreferredLanguage.EN);
        user.setBirthDate(java.time.LocalDate.of(1994, 6, 18));
        user.setCountryCode(com.grun.calorietracker.enums.CountryCode.IE);
        user.setEmailVerified(true);
        user.setPasswordSet(true);

        when(fastingProgramRepository.findAllByUserIdOrderByCreatedAtDesc(10L)).thenReturn(java.util.List.of());
        when(advancedFastingReminderSettingsRepository.findByUser(user)).thenReturn(Optional.empty());
        when(fastingScheduleExceptionRepository.findAllByUserIdOrderBySourceDateAsc(10L)).thenReturn(java.util.List.of());
        when(fastingHistoryCorrectionRepository.findAllByUserIdOrderByCreatedAtDesc(10L)).thenReturn(java.util.List.of());
        when(fastingScheduleExceptionAuditRepository.findAllByUserIdOrderByCreatedAtDesc(10L)).thenReturn(java.util.List.of());
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
        when(mealPlanRepository.countByUser(user)).thenReturn(2L);
        when(recipeLogRepository.countByUser(user)).thenReturn(12L);
        when(recipeUserInteractionRepository.countByUser(user)).thenReturn(4L);
        when(failedBarcodeScanRepository.countByUser(user)).thenReturn(1L);
        when(productCorrectionSuggestionRepository.countByUser(user)).thenReturn(2L);
        when(productAnalyticsEventRepository.countByUser(user)).thenReturn(13L);

        com.grun.calorietracker.entity.MealPlanEntity plan = new com.grun.calorietracker.entity.MealPlanEntity();
        plan.setId(77L);
        plan.setUser(user);
        plan.setName("AI week");
        plan.setStartDate(java.time.LocalDate.of(2026, 7, 20));
        plan.setEndDate(java.time.LocalDate.of(2026, 7, 26));
        plan.setGenerationMode(com.grun.calorietracker.enums.NutritionPlanGenerationMode.GENERAL);
        com.grun.calorietracker.entity.MealPlanItemEntity planItem = new com.grun.calorietracker.entity.MealPlanItemEntity();
        planItem.setId(88L);
        planItem.setMealPlan(plan);
        planItem.setPlanDate(java.time.LocalDate.of(2026, 7, 20));
        planItem.setMealType("LUNCH");
        planItem.setItemType(com.grun.calorietracker.enums.MealPlanItemType.AI_SNAPSHOT);
        planItem.setPortionSize(430.0);
        planItem.setPortionUnit(com.grun.calorietracker.enums.FoodPortionUnit.GRAM);
        planItem.setSnapshotName("Chicken Bowl");
        planItem.setSnapshotCalories(520.0);
        planItem.setSnapshotProtein(48.0);
        planItem.setSnapshotCarbs(55.0);
        planItem.setSnapshotFat(11.0);
        plan.getItems().add(planItem);
        when(mealPlanRepository.findByUserOrderByStartDateDesc(user)).thenReturn(java.util.List.of(plan));

        UserNutritionPreferenceEntity preference = new UserNutritionPreferenceEntity();
        preference.setUser(user);
        preference.setAllergens(java.util.Set.of(RecipeAllergen.MILK));
        preference.setExcludedFoods(java.util.List.of("Pork"));
        preference.setDietaryPreferences(java.util.List.of("High protein"));
        when(userNutritionPreferenceRepository.findByUser(user)).thenReturn(Optional.of(preference));

        com.grun.calorietracker.entity.UserFitnessPreferenceEntity fitnessPreference =
                new com.grun.calorietracker.entity.UserFitnessPreferenceEntity();
        fitnessPreference.setUser(user);
        fitnessPreference.setWeeklyWorkoutFrequency(
                com.grun.calorietracker.enums.WeeklyWorkoutFrequency.THREE_TO_FOUR);
        fitnessPreference.setUpdatedAt(java.time.LocalDateTime.of(2026, 7, 24, 10, 0));
        when(userFitnessPreferenceRepository.findByUser(user)).thenReturn(Optional.of(fitnessPreference));
        GdprDataExportDto dto = service.exportMyData("user@grun.app");

        assertEquals("user@grun.app", dto.getEmail());
        assertEquals(java.time.LocalDate.of(1994, 6, 18), dto.getBirthDate());
        assertEquals(com.grun.calorietracker.enums.CountryCode.IE, dto.getCountryCode());
        assertEquals(com.grun.calorietracker.enums.WeeklyWorkoutFrequency.THREE_TO_FOUR,
                dto.getFitnessPreferences().getWeeklyWorkoutFrequency());
        assertEquals(11L, dto.getFoodLogCount());
        assertEquals(2L, dto.getConsentCount());
        assertEquals(6L, dto.getAiRequestCount());
        assertEquals(9L, dto.getWaterLogCount());
        assertEquals(3L, dto.getFastingSessionCount());
        assertEquals(2L, dto.getMealPlanCount());
        assertEquals(12L, dto.getRecipeLogCount());
        assertEquals(4L, dto.getRecipeInteractionCount());
        assertEquals(1L, dto.getFailedBarcodeScanCount());
        assertEquals(2L, dto.getProductCorrectionSuggestionCount());
        assertEquals(13L, dto.getProductAnalyticsEventCount());
        assertEquals(100, dto.getSubscription().getAiMonthlyQuota());
        assertEquals(java.util.Set.of(RecipeAllergen.MILK), dto.getNutritionPreferences().getAllergens());
        assertEquals(java.util.List.of("Pork"), dto.getNutritionPreferences().getExcludedFoods());
        assertEquals(0, dto.getFoodLogs().size());
        assertEquals("GENERAL", dto.getMealPlans().get(0).getGenerationMode());
        assertEquals("Chicken Bowl", dto.getMealPlans().get(0).getItems().get(0).getSnapshotName());
        assertEquals(520.0, dto.getMealPlans().get(0).getItems().get(0).getSnapshotNutrition().getCalories());
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
        verify(fastingProgramRepository).deleteByUser(user);
        verify(mealPlanRepository).deleteByUser(user);
        verify(recipeLogRepository).deleteByUser(user);
        verify(stepGoalRepository).deleteByUser(user);
        verify(userPushTokenRepository).deleteByUser(user);
        verify(recipeUserInteractionRepository).deleteByUser(user);
        verify(failedBarcodeScanRepository).deleteByUser(user);
        verify(productCorrectionSuggestionRepository).deleteByUser(user);
        verify(productAnalyticsEventRepository).deleteByUser(user);
        verify(userNutritionPreferenceRepository).deleteByUser(user);
        verify(userFitnessPreferenceRepository).deleteByUser(user);
        verify(sleepSessionRepository).deleteByUser(user);
        verify(sleepGoalRepository).deleteByUser(user);
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
