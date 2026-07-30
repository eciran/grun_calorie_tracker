package com.grun.calorietracker.service;

import com.grun.calorietracker.exception.SubscriptionFeatureAccessDeniedException;

import com.grun.calorietracker.dto.AdminSubscriptionUpdateRequestDto;
import com.grun.calorietracker.entity.SubscriptionPlanFeatureEntity;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.entity.SubscriptionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserSubscriptionEntitlementEntity;
import com.grun.calorietracker.enums.BillingPeriod;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionStatus;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.SubscriptionPlanFeatureRepository;
import com.grun.calorietracker.repository.SubscriptionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.repository.UserSubscriptionEntitlementRepository;
import com.grun.calorietracker.service.MailDeliveryService;
import com.grun.calorietracker.service.impl.SubscriptionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;

class SubscriptionServiceImplTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionPlanFeatureRepository subscriptionPlanFeatureRepository;

    @Mock
    private UserSubscriptionEntitlementRepository userSubscriptionEntitlementRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private MailDeliveryService mailDeliveryService;

    @Mock
    private AiCreditPricingService aiCreditPricingService;

    private SubscriptionServiceImpl service;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new SubscriptionServiceImpl(
                subscriptionRepository, userRepository, subscriptionPlanFeatureRepository,
                userSubscriptionEntitlementRepository, notificationRepository, mailDeliveryService,
                aiCreditPricingService);
        user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");
        lenient().when(subscriptionPlanFeatureRepository.findByPlanTypeAndFeature(any(), any())).thenReturn(Optional.empty());
        lenient().when(subscriptionPlanFeatureRepository.findByPlanTypeOrderByFeatureAsc(any())).thenReturn(emptyList());
        lenient().when(aiCreditPricingService.fixedCost(any())).thenReturn(1);
        lenient().when(userSubscriptionEntitlementRepository.findBySubscription(any())).thenReturn(emptyList());
        lenient().when(userSubscriptionEntitlementRepository.countBySubscription(any())).thenReturn(0L);
        lenient().when(userSubscriptionEntitlementRepository.existsActiveFeature(anyLong(), any(), any(), any())).thenReturn(false);
        lenient().when(userSubscriptionEntitlementRepository.existsActiveEntitlementForSubscription(anyLong(), any(), any())).thenReturn(false);
        lenient().when(userSubscriptionEntitlementRepository.findActiveFeaturesForSubscription(anyLong(), any(), any())).thenReturn(emptyList());
        lenient().when(userSubscriptionEntitlementRepository.findActiveEntitlementsForPlanFeature(any(), any(), any())).thenReturn(emptyList());
    }

    @Test
    void consumeAiQuota_singleArgumentEntryPoint_isTransactional() throws Exception {
        assertNotNull(SubscriptionServiceImpl.class
                .getMethod("consumeAiQuota", String.class)
                .getAnnotation(Transactional.class));
    }

    @Test
    void getCurrentSubscription_whenMissing_returnsFreeDefaults() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.empty());

        SubscriptionDto result = service.getCurrentSubscription("user@example.com");

        assertEquals(SubscriptionPlan.FREE, result.getPlanType());
        assertEquals(SubscriptionStatus.ACTIVE, result.getStatus());
        assertEquals(BillingPeriod.NONE, result.getBillingPeriod());
        assertEquals(0, result.getAiMonthlyQuota());
        assertEquals(0, result.getAiRemainingThisPeriod());
        assertEquals(true, result.getActiveEntitlement());
        assertEquals(false, result.getAiAccessAllowed());
    }

    @Test
    void updateUserSubscription_createsPlusPlanWithQuotaState() {
        AdminSubscriptionUpdateRequestDto request = new AdminSubscriptionUpdateRequestDto();
        request.setPlanType(SubscriptionPlan.PLUS);
        request.setStatus(SubscriptionStatus.ACTIVE);
        request.setBillingPeriod(BillingPeriod.MONTHLY);
        request.setStartDate(java.time.LocalDate.of(2026, 5, 1));
        request.setAiUsedThisPeriod(4);


        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionDto result = service.updateUserSubscription(1L, request);

        assertEquals(SubscriptionPlan.PLUS, result.getPlanType());
        assertEquals(BillingPeriod.MONTHLY, result.getBillingPeriod());
        assertEquals(50, result.getAiMonthlyQuota());
        assertEquals(0, result.getAiAddonQuota());
        assertEquals(50, result.getAiTotalQuotaThisPeriod());
        assertEquals(4, result.getAiUsedThisPeriod());
        assertEquals(46, result.getAiBaseRemainingThisPeriod());
        assertEquals(0, result.getAiAddonRemainingThisPeriod());
        assertEquals(46, result.getAiRemainingThisPeriod());
        assertEquals(java.time.LocalDate.of(2026, 6, 1), result.getQuotaResetDate());
        assertEquals(true, result.getAutoRenew());
    }

    @Test
    void getCurrentSubscription_whenExpired_returnsInactiveEntitlement() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE, 15, 5);
        entity.setEndDate(java.time.LocalDate.now().minusDays(1));

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));

        SubscriptionDto result = service.getCurrentSubscription("user@example.com");

        assertEquals(false, result.getActiveEntitlement());
        assertEquals(false, result.getAiAccessAllowed());
        assertEquals(10, result.getAiRemainingThisPeriod());
    }

    @Test
    void getCurrentSubscription_whenPeriodDatesExist_returnsStoredQuotaResetDate() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE, 15, 5);
        entity.setAiQuotaPeriodStartDate(java.time.LocalDate.of(2026, 5, 10));
        entity.setAiQuotaPeriodEndDate(java.time.LocalDate.of(2026, 6, 9));

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));

        SubscriptionDto result = service.getCurrentSubscription("user@example.com");

        assertEquals(java.time.LocalDate.of(2026, 6, 10), result.getQuotaResetDate());
    }

    @Test
    void getFeatureAccess_whenPlusPlan_returnsPlusFeatureMatrix() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE, 15, 5);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));
        when(aiCreditPricingService.fixedCost(SubscriptionFeature.AI_INSIGHTS)).thenReturn(3);

        var result = service.getFeatureAccess("user@example.com");

        assertEquals(SubscriptionPlan.PLUS, result.getPlanType());
        assertEquals(true, result.getAiWorkoutPlanner());
        assertEquals(true, result.getHealthIntegration());
        assertEquals(true, result.getNextMealSuggestions());
        assertEquals(false, result.getGroceryList());
        assertEquals(true, result.getRecipeBuilder());
        assertEquals(true, result.getPublicRecipeLibrary());
        assertEquals(false, result.getAdvancedAnalytics());
        assertEquals(true, result.getMicronutrientDetails());
        assertEquals(false, result.getMicronutrientAnalytics());
        assertEquals(true, result.getAdFree());
        assertEquals(true, result.getCustomFoodLibrary());
        assertEquals(3, result.getAiInsightsCreditCost());
        assertEquals(3, result.getAiCreditCosts().get(SubscriptionFeature.AI_INSIGHTS));
        assertEquals(1, result.getAiCreditCosts().get(SubscriptionFeature.AI_WORKOUT_PLANNER));
        assertEquals(10, result.getAiRemainingThisPeriod());
    }
    @Test
    void getFeatureAccess_whenProPlan_enablesAdvancedAnalytics() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE, 150, 0);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));

        var result = service.getFeatureAccess("user@example.com");

        assertEquals(SubscriptionPlan.PRO, result.getPlanType());
        assertEquals(true, result.getAdvancedAnalytics());
        assertEquals(true, result.getMicronutrientDetails());
        assertEquals(true, result.getMicronutrientAnalytics());
        assertEquals(true, result.getGroceryList());
    }

    @Test
    void getUserFeatureAccessForAdmin_whenSubscriptionExists_returnsResolvedAccess() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.FREE, SubscriptionStatus.ACTIVE, 0, 0);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));
        when(subscriptionPlanFeatureRepository.findByPlanTypeAndFeature(SubscriptionPlan.FREE, SubscriptionFeature.AI_RECIPE_GENERATION))
                .thenReturn(Optional.of(planFeature(SubscriptionPlan.FREE, SubscriptionFeature.AI_RECIPE_GENERATION, true)));

        var result = service.getUserFeatureAccessForAdmin(1L);

        assertEquals(SubscriptionPlan.FREE, result.getPlanType());
        assertEquals(false, result.getAiRecipeGeneration());
        assertEquals(false, result.getNextMealSuggestions());
        assertEquals(0, result.getAiRemainingThisPeriod());
    }

    @Test
    void hasFeatureAccess_whenFeatureAllowed_returnsTrue() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE, 100, 10);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));

        assertEquals(true, service.hasFeatureAccess("user@example.com", SubscriptionFeature.AD_FREE));
    }

    @Test
    void hasFeatureAccess_whenNextMealSuggestionsOnFreePlan_returnsFalse() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.FREE, SubscriptionStatus.ACTIVE, 0, 0);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));

        assertEquals(false, service.hasFeatureAccess("user@example.com", SubscriptionFeature.NEXT_MEAL_SUGGESTIONS));
    }

    @Test
    void hasFeatureAccess_forGroceryList_isProOnlyByDefault() {
        SubscriptionEntity free = subscription(SubscriptionPlan.FREE, SubscriptionStatus.ACTIVE, 0, 0);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(free));

        assertEquals(false, service.hasFeatureAccess("user@example.com", SubscriptionFeature.GROCERY_LIST));

        SubscriptionEntity plus = subscription(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE, 15, 0);
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(plus));
        assertEquals(false, service.hasFeatureAccess("user@example.com", SubscriptionFeature.GROCERY_LIST));

        SubscriptionEntity pro = subscription(SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE, 150, 0);
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(pro));
        assertEquals(true, service.hasFeatureAccess("user@example.com", SubscriptionFeature.GROCERY_LIST));
    }

    @Test
    void hasFeatureAccess_onFreePlan_allowsRecipeBuilderButRestrictsPublicLibrary() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.FREE, SubscriptionStatus.ACTIVE, 0, 0);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));

        assertEquals(true, service.hasFeatureAccess("user@example.com", SubscriptionFeature.RECIPE_BUILDER));
        assertEquals(false, service.hasFeatureAccess("user@example.com", SubscriptionFeature.PUBLIC_RECIPE_LIBRARY));
    }

    @Test
    void hasFeatureAccess_whenHealthIntegrationOnFreePlan_returnsFalse() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.FREE, SubscriptionStatus.ACTIVE, 0, 0);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));

        assertEquals(false, service.hasFeatureAccess("user@example.com", SubscriptionFeature.HEALTH_INTEGRATION));
    }

    @Test
    void hasFeatureAccess_whenSnapshotExists_usesSnapshotBeforeCurrentPlanMatrix() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE, 15, 0);
        entity.setId(7L);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));
        when(userSubscriptionEntitlementRepository.findActiveFeaturesForSubscription(eq(7L), eq(SubscriptionPlan.PLUS), any()))
                .thenReturn(List.of(SubscriptionFeature.WATER_TRACKING));

        assertEquals(false, service.hasFeatureAccess("user@example.com", SubscriptionFeature.HEALTH_INTEGRATION));
    }

    @Test
    void getFeatureAccess_resolvesSnapshotWithOneBulkQuery() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE, 15, 0);
        entity.setId(7L);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));
        when(userSubscriptionEntitlementRepository.findActiveFeaturesForSubscription(
                eq(7L), eq(SubscriptionPlan.PLUS), any()))
                .thenReturn(List.of(SubscriptionFeature.HEALTH_INTEGRATION));

        var result = service.getFeatureAccess("user@example.com");

        assertEquals(true, result.getHealthIntegration());
        assertEquals(false, result.getWaterTracking());
        verify(userSubscriptionEntitlementRepository)
                .findActiveFeaturesForSubscription(eq(7L), eq(SubscriptionPlan.PLUS), any());
        verify(userSubscriptionEntitlementRepository, never())
                .existsActiveEntitlementForSubscription(anyLong(), any(), any());
        verify(userSubscriptionEntitlementRepository, never())
                .existsActiveFeature(anyLong(), any(), any(), any());
        verify(subscriptionPlanFeatureRepository, never()).findByPlanTypeOrderByFeatureAsc(any());
    }

    @Test
    void updatePlanFeature_updatesAdminManagedMatrixRule() {
        SubscriptionPlanFeatureEntity entity = new SubscriptionPlanFeatureEntity();

        when(subscriptionPlanFeatureRepository.findByPlanTypeAndFeature(SubscriptionPlan.PLUS, SubscriptionFeature.HEALTH_INTEGRATION))
                .thenReturn(Optional.of(entity));
        when(subscriptionPlanFeatureRepository.save(any(SubscriptionPlanFeatureEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.updatePlanFeature(
                SubscriptionPlan.PLUS,
                SubscriptionFeature.HEALTH_INTEGRATION,
                false,
                java.time.LocalDate.of(2026, 6, 1)
        );

        assertEquals(SubscriptionPlan.PLUS, result.getPlanType());
        assertEquals(SubscriptionFeature.HEALTH_INTEGRATION, result.getFeature());
        assertEquals(false, result.getEnabled());
        assertEquals(java.time.LocalDate.of(2026, 6, 1), result.getEffectiveFrom());
    }

    @Test
    void applyCurrentFeatureMatrixToUser_refreshesActiveSnapshotFromCurrentPlanMatrix() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE, 15, 0);
        entity.setId(7L);
        UserSubscriptionEntitlementEntity oldEntitlement = new UserSubscriptionEntitlementEntity();
        oldEntitlement.setSubscription(entity);
        oldEntitlement.setUser(user);
        oldEntitlement.setFeature(SubscriptionFeature.HEALTH_INTEGRATION);
        oldEntitlement.setEnabled(true);
        oldEntitlement.setSourcePlan(SubscriptionPlan.PLUS);
        oldEntitlement.setValidFrom(java.time.LocalDate.now().minusDays(10));
        oldEntitlement.setValidUntil(null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));
        when(userSubscriptionEntitlementRepository.findBySubscription(entity)).thenReturn(List.of(oldEntitlement));
        when(subscriptionPlanFeatureRepository.findByPlanTypeOrderByFeatureAsc(SubscriptionPlan.PLUS))
                .thenReturn(List.of(planFeature(SubscriptionPlan.PLUS, SubscriptionFeature.WATER_TRACKING, true)));
        when(userSubscriptionEntitlementRepository.findActiveFeaturesForSubscription(eq(7L), eq(SubscriptionPlan.PLUS), any()))
                .thenReturn(List.of(SubscriptionFeature.WATER_TRACKING));

        var result = service.applyCurrentFeatureMatrixToUser(1L);

        assertEquals(true, result.getWaterTracking());
        assertEquals(false, oldEntitlement.getEnabled());
        assertEquals(java.time.LocalDate.now().minusDays(1), oldEntitlement.getValidUntil());
        ArgumentCaptor<UserSubscriptionEntitlementEntity> captor = ArgumentCaptor.forClass(UserSubscriptionEntitlementEntity.class);
        verify(userSubscriptionEntitlementRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        boolean waterSnapshotCreated = captor.getAllValues().stream()
                .anyMatch(item -> item.getFeature() == SubscriptionFeature.WATER_TRACKING
                        && item.getSourcePlan() == SubscriptionPlan.PLUS
                        && Boolean.TRUE.equals(item.getEnabled()));
        assertEquals(true, waterSnapshotCreated);
    }
    @Test
    void assertFeatureAccess_whenFeatureDenied_throwsSubscriptionAccessDenied() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.FREE, SubscriptionStatus.ACTIVE, 0, 0);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));

        assertThrows(SubscriptionFeatureAccessDeniedException.class,
                () -> service.assertFeatureAccess("user@example.com", SubscriptionFeature.HEALTH_INTEGRATION));
    }

    @Test
    void consumeAiQuota_whenQuotaAvailable_incrementsUsage() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE, 15, 14);

        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionDto result = service.consumeAiQuota("user@example.com");

        assertEquals(15, result.getAiUsedThisPeriod());
        assertEquals(0, result.getAiRemainingThisPeriod());
        assertEquals(false, result.getAiAccessAllowed());
        assertEquals(true, result.getUpgradeRecommended());
    }

    @Test
    void consumeAiQuota_whenAddonAvailable_consumesAddonBeforeBaseQuota() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE, 15, 0);
        entity.setAiAddonQuota(1);
        entity.setAiAddonUsed(0);
        entity.setAiAddonQuotaExpiresAt(java.time.LocalDate.now().plusDays(1));

        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionDto result = service.consumeAiQuota("user@example.com");

        assertEquals(1, result.getAiUsedThisPeriod());
        assertEquals(1, result.getAiAddonUsed());
        assertEquals(15, result.getAiBaseRemainingThisPeriod());
        assertEquals(0, result.getAiAddonRemainingThisPeriod());
        assertEquals(15, result.getAiRemainingThisPeriod());
    }

    @Test
    void consumeAiQuota_withEightCreditCost_consumesThreeAddonAndFiveBaseCredits() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE, 15, 0);
        entity.setAiAddonQuota(3);
        entity.setAiAddonUsed(0);
        entity.setAiAddonQuotaExpiresAt(java.time.LocalDate.now().plusDays(1));

        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionDto result = service.consumeAiQuota("user@example.com", 8);

        assertEquals(8, result.getAiUsedThisPeriod());
        assertEquals(3, result.getAiAddonUsed());
        assertEquals(10, result.getAiBaseRemainingThisPeriod());
        assertEquals(0, result.getAiAddonRemainingThisPeriod());
        assertEquals(10, result.getAiRemainingThisPeriod());
    }
    @Test
    void consumeAiQuota_withConfiguredAmount_incrementsUsageAtomically() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE, 15, 5);
        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionDto result = service.consumeAiQuota("user@example.com", 3);

        assertEquals(8, result.getAiUsedThisPeriod());
        assertEquals(7, result.getAiRemainingThisPeriod());
    }

    @Test
    void consumeAiQuota_whenConfiguredAmountExceedsRemaining_rejectsWithoutSave() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE, 15, 14);
        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));

        assertThrows(IllegalArgumentException.class, () -> service.consumeAiQuota("user@example.com", 2));
        verify(subscriptionRepository, never()).save(any());
    }
    @Test
    void consumeAiQuota_whenPeriodExpired_resetsUsageBeforeConsume() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE, 15, 15);
        entity.setAiQuotaPeriodStartDate(java.time.LocalDate.now().minusMonths(1));
        entity.setAiQuotaPeriodEndDate(java.time.LocalDate.now().minusDays(1));

        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionDto result = service.consumeAiQuota("user@example.com");

        assertEquals(1, result.getAiUsedThisPeriod());
        assertEquals(14, result.getAiRemainingThisPeriod());
    }

    @Test
    void consumeAiQuota_whenBaseQuotaConsumedAndAddonAvailable_consumesAddonCapacity() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE, 100, 100);
        entity.setAiAddonQuota(50);
        entity.setAiAddonQuotaExpiresAt(java.time.LocalDate.now().plusDays(6));
        entity.setAiAddonUsed(0);

        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionDto result = service.consumeAiQuota("user@example.com");

        assertEquals(101, result.getAiUsedThisPeriod());
        assertEquals(1, result.getAiAddonUsed());
        assertEquals(0, result.getAiBaseRemainingThisPeriod());
        assertEquals(49, result.getAiAddonRemainingThisPeriod());
        assertEquals(49, result.getAiRemainingThisPeriod());
        assertEquals(true, result.getAiAccessAllowed());
    }

    @Test
    void resetUserAiQuota_resetsUsageWithoutChangingPlan() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE, 100, 44);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.of(entity));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionDto result = service.resetUserAiQuota(1L);

        assertEquals(SubscriptionPlan.PRO, result.getPlanType());
        assertEquals(0, result.getAiUsedThisPeriod());
        assertEquals(100, result.getAiRemainingThisPeriod());
    }

    @Test
    void refundConsumedAiQuota_decreasesUsedQuotaWithoutChangingTotalQuota() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE, 15, 7);

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.of(entity));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionDto result = service.refundConsumedAiQuota(1L, 3);

        assertEquals(15, result.getAiMonthlyQuota());
        assertEquals(4, result.getAiUsedThisPeriod());
        assertEquals(11, result.getAiRemainingThisPeriod());
    }

    @Test
    void refundConsumedAiQuota_whenAddonWasConsumed_restoresAddonBeforeBaseQuota() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE, 15, 1);
        entity.setAiAddonQuota(1);
        entity.setAiAddonUsed(1);
        entity.setAiAddonQuotaExpiresAt(java.time.LocalDate.now().plusDays(1));

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.of(entity));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionDto result = service.refundConsumedAiQuota(1L, 1);

        assertEquals(0, result.getAiUsedThisPeriod());
        assertEquals(0, result.getAiAddonUsed());
        assertEquals(15, result.getAiBaseRemainingThisPeriod());
        assertEquals(1, result.getAiAddonRemainingThisPeriod());
        assertEquals(16, result.getAiRemainingThisPeriod());
    }

    @Test
    void refundConsumedAiQuota_whenAmountExceedsUsedQuota_rejects() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE, 15, 2);

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.of(entity));

        assertThrows(IllegalArgumentException.class, () -> service.refundConsumedAiQuota(1L, 3));
    }

    @Test
    void grantAiAddonQuota_addsOneOffQuotaWithoutChangingBasePlan() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE, 100, 100);
        entity.setAiAddonQuota(20);
        entity.setAiAddonQuotaExpiresAt(java.time.LocalDate.now().plusDays(3));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.of(entity));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionDto result = service.grantAiAddonQuota(1L, 50, 7);

        assertEquals(SubscriptionPlan.PRO, result.getPlanType());
        assertEquals(100, result.getAiMonthlyQuota());
        assertEquals(70, result.getAiAddonQuota());
        assertEquals(java.time.LocalDate.now().plusDays(7), result.getAiAddonQuotaExpiresAt());
        assertEquals(170, result.getAiTotalQuotaThisPeriod());
        assertEquals(70, result.getAiRemainingThisPeriod());
    }

    @Test
    void grantAiAddonQuota_whenAmountIsZero_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> service.grantAiAddonQuota(1L, 0, 7));
    }

    @Test
    void getCurrentSubscription_whenAddonExpired_ignoresAddonQuota() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE, 100, 100);
        entity.setAiAddonQuota(50);
        entity.setAiAddonQuotaExpiresAt(java.time.LocalDate.now().minusDays(1));

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));

        SubscriptionDto result = service.getCurrentSubscription("user@example.com");

        assertEquals(0, result.getAiAddonQuota());
        assertEquals(null, result.getAiAddonQuotaExpiresAt());
        assertEquals(100, result.getAiTotalQuotaThisPeriod());
        assertEquals(0, result.getAiRemainingThisPeriod());
    }

    @Test
    void getCurrentSubscription_whenConsumedAddonExpires_doesNotChargeBaseQuota() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE, 100, 6);
        entity.setAiAddonQuota(10);
        entity.setAiAddonUsed(6);
        entity.setAiAddonQuotaExpiresAt(java.time.LocalDate.now().minusDays(1));

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));

        SubscriptionDto result = service.getCurrentSubscription("user@example.com");

        assertEquals(0, result.getAiAddonQuota());
        assertEquals(0, result.getAiAddonUsed());
        assertEquals(0, result.getAiUsedThisPeriod());
        assertEquals(100, result.getAiBaseRemainingThisPeriod());
    }

    @Test
    void consumeAiQuota_whenBasePeriodResets_keepsUnexpiredOneOffAddonQuota() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE, 100, 100);
        entity.setAiAddonQuota(15);
        entity.setAiAddonQuotaExpiresAt(java.time.LocalDate.now().plusDays(5));
        entity.setAiAddonUsed(10);
        entity.setAiQuotaPeriodStartDate(java.time.LocalDate.now().minusMonths(1));
        entity.setAiQuotaPeriodEndDate(java.time.LocalDate.now().minusDays(1));

        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionDto result = service.consumeAiQuota("user@example.com");

        assertEquals(15, result.getAiAddonQuota());
        assertEquals(java.time.LocalDate.now().plusDays(5), result.getAiAddonQuotaExpiresAt());
        assertEquals(11, result.getAiUsedThisPeriod());
        assertEquals(11, result.getAiAddonUsed());
        assertEquals(104, result.getAiRemainingThisPeriod());
    }

    @Test
    void consumeAiQuota_whenQuotaUnavailable_throwsIllegalArgumentException() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.FREE, SubscriptionStatus.ACTIVE, 0, 0);

        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));

        assertThrows(IllegalArgumentException.class, () -> service.consumeAiQuota("user@example.com"));
    }

    @Test
    void updateUserSubscription_whenEndDateBeforeStartDate_throwsIllegalArgumentException() {
        AdminSubscriptionUpdateRequestDto request = new AdminSubscriptionUpdateRequestDto();
        request.setPlanType(SubscriptionPlan.PLUS);
        request.setStatus(SubscriptionStatus.ACTIVE);
        request.setBillingPeriod(BillingPeriod.MONTHLY);
        request.setStartDate(java.time.LocalDate.of(2026, 5, 20));
        request.setEndDate(java.time.LocalDate.of(2026, 5, 19));

        assertThrows(IllegalArgumentException.class, () -> service.updateUserSubscription(1L, request));
    }

    @Test
    void updateUserSubscription_whenActivePaidPlanAlreadyExpired_throwsIllegalArgumentException() {
        AdminSubscriptionUpdateRequestDto request = new AdminSubscriptionUpdateRequestDto();
        request.setPlanType(SubscriptionPlan.PRO);
        request.setStatus(SubscriptionStatus.ACTIVE);
        request.setBillingPeriod(BillingPeriod.MONTHLY);
        request.setStartDate(java.time.LocalDate.now().minusMonths(1));
        request.setEndDate(java.time.LocalDate.now().minusDays(1));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateUserSubscription(1L, request)
        );

        assertEquals("An active paid subscription end date must be today or later.", exception.getMessage());
    }
    @Test
    void updateUserSubscription_whenAiUsageExceedsQuota_throwsIllegalArgumentException() {
        AdminSubscriptionUpdateRequestDto request = new AdminSubscriptionUpdateRequestDto();
        request.setPlanType(SubscriptionPlan.PLUS);
        request.setStatus(SubscriptionStatus.ACTIVE);
        request.setBillingPeriod(BillingPeriod.MONTHLY);
        request.setAiMonthlyQuota(15);
        request.setAiUsedThisPeriod(16);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.updateUserSubscription(1L, request));
    }

    @Test
    void updateUserSubscription_whenDowngradedToFree_revokesActiveSnapshotsImmediately() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE, 100, 10);
        entity.setId(7L);
        entity.setAiAddonQuota(15);
        entity.setAiAddonQuotaExpiresAt(java.time.LocalDate.now().plusDays(5));
        UserSubscriptionEntitlementEntity entitlement = new UserSubscriptionEntitlementEntity();
        entitlement.setSubscription(entity);
        entitlement.setUser(user);
        entitlement.setFeature(SubscriptionFeature.AD_FREE);
        entitlement.setEnabled(true);
        entitlement.setSourcePlan(SubscriptionPlan.PRO);
        entitlement.setValidFrom(java.time.LocalDate.now().minusDays(10));
        entitlement.setValidUntil(null);

        AdminSubscriptionUpdateRequestDto request = new AdminSubscriptionUpdateRequestDto();
        request.setPlanType(SubscriptionPlan.FREE);
        request.setStatus(SubscriptionStatus.ACTIVE);
        request.setBillingPeriod(BillingPeriod.NONE);
        request.setStartDate(java.time.LocalDate.now());
        request.setAiMonthlyQuota(0);
        request.setAiUsedThisPeriod(10);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.of(entity));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userSubscriptionEntitlementRepository.findBySubscription(entity)).thenReturn(List.of(entitlement));

        SubscriptionDto result = service.updateUserSubscription(1L, request);

        assertEquals(SubscriptionPlan.FREE, result.getPlanType());
        assertEquals(0, result.getAiMonthlyQuota());
        assertEquals(0, result.getAiUsedThisPeriod());
        assertEquals(0, result.getAiAddonQuota());
        assertNull(result.getAiAddonQuotaExpiresAt());
        assertEquals(false, entitlement.getEnabled());
        assertEquals(java.time.LocalDate.now().minusDays(1), entitlement.getValidUntil());
        verify(userSubscriptionEntitlementRepository).save(entitlement);
    }

    @Test
    void getFeatureAccess_whenOnlyInactiveSnapshotsExist_usesCurrentPlanMatrix() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE, 15, 0);
        entity.setId(7L);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));
        var result = service.getFeatureAccess("user@example.com");

        assertEquals(true, result.getHealthIntegration());
        assertEquals(true, result.getNextMealSuggestions());
        assertEquals(false, result.getGroceryList());
        verify(subscriptionPlanFeatureRepository).findByPlanTypeOrderByFeatureAsc(SubscriptionPlan.PLUS);
    }
    private SubscriptionEntity subscription(SubscriptionPlan plan, SubscriptionStatus status, int quota, int used) {
        java.time.LocalDate today = java.time.LocalDate.now();
        SubscriptionEntity entity = new SubscriptionEntity();
        entity.setUser(user);
        entity.setPlanType(plan);
        entity.setStatus(status);
        entity.setBillingPeriod(BillingPeriod.MONTHLY);
        entity.setStartDate(today.minusDays(1));
        entity.setAiQuotaPeriodStartDate(today.minusDays(1));
        entity.setAiQuotaPeriodEndDate(today.plusDays(29));
        entity.setAiMonthlyQuota(quota);
        entity.setAiAddonQuota(0);
        entity.setAiUsedThisPeriod(used);
        entity.setAutoRenew(true);
        return entity;
    }
    private SubscriptionPlanFeatureEntity planFeature(SubscriptionPlan plan, SubscriptionFeature feature, boolean enabled) {
        SubscriptionPlanFeatureEntity entity = new SubscriptionPlanFeatureEntity();
        entity.setPlanType(plan);
        entity.setFeature(feature);
        entity.setEnabled(enabled);
        entity.setEffectiveFrom(java.time.LocalDate.now());
        return entity;
    }
}
