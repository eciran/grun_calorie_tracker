package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminSubscriptionUpdateRequestDto;
import com.grun.calorietracker.entity.SubscriptionPlanFeatureEntity;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.entity.SubscriptionEntity;
import com.grun.calorietracker.entity.UserEntity;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
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

    @InjectMocks
    private SubscriptionServiceImpl service;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");
        lenient().when(subscriptionPlanFeatureRepository.findByPlanTypeAndFeature(any(), any())).thenReturn(Optional.empty());
        lenient().when(userSubscriptionEntitlementRepository.findBySubscription(any())).thenReturn(emptyList());
        lenient().when(userSubscriptionEntitlementRepository.countBySubscription(any())).thenReturn(0L);
        lenient().when(userSubscriptionEntitlementRepository.existsActiveFeature(anyLong(), any(), any())).thenReturn(false);
        lenient().when(userSubscriptionEntitlementRepository.findActiveEntitlementsForPlanFeature(any(), any(), any())).thenReturn(emptyList());
    }

    @Test
    void getCurrentSubscription_whenMissing_returnsFreeDefaults() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.empty());

        SubscriptionDto result = service.getCurrentSubscription("user@example.com");

        assertEquals(SubscriptionPlan.FREE, result.getPlanType());
        assertEquals(SubscriptionStatus.ACTIVE, result.getStatus());
        assertEquals(BillingPeriod.NONE, result.getBillingPeriod());
        assertEquals(3, result.getAiMonthlyQuota());
        assertEquals(3, result.getAiRemainingThisPeriod());
        assertEquals(true, result.getActiveEntitlement());
        assertEquals(true, result.getAiAccessAllowed());
    }

    @Test
    void updateUserSubscription_createsPlusPlanWithQuotaState() {
        AdminSubscriptionUpdateRequestDto request = new AdminSubscriptionUpdateRequestDto();
        request.setPlanType(SubscriptionPlan.PLUS);
        request.setStatus(SubscriptionStatus.ACTIVE);
        request.setBillingPeriod(BillingPeriod.MONTHLY);
        request.setStartDate(java.time.LocalDate.of(2026, 5, 1));
        request.setAiUsedThisPeriod(4);
        request.setAutoRenew(true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionDto result = service.updateUserSubscription(1L, request);

        assertEquals(SubscriptionPlan.PLUS, result.getPlanType());
        assertEquals(BillingPeriod.MONTHLY, result.getBillingPeriod());
        assertEquals(15, result.getAiMonthlyQuota());
        assertEquals(0, result.getAiAddonQuota());
        assertEquals(15, result.getAiTotalQuotaThisPeriod());
        assertEquals(4, result.getAiUsedThisPeriod());
        assertEquals(11, result.getAiBaseRemainingThisPeriod());
        assertEquals(0, result.getAiAddonRemainingThisPeriod());
        assertEquals(11, result.getAiRemainingThisPeriod());
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

        var result = service.getFeatureAccess("user@example.com");

        assertEquals(SubscriptionPlan.PLUS, result.getPlanType());
        assertEquals(true, result.getAiWorkoutPlanner());
        assertEquals(true, result.getHealthIntegration());
        assertEquals(true, result.getAdvancedAnalytics());
        assertEquals(false, result.getAdFree());
        assertEquals(true, result.getCustomFoodLibrary());
        assertEquals(10, result.getAiRemainingThisPeriod());
    }

    @Test
    void hasFeatureAccess_whenFeatureAllowed_returnsTrue() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE, 100, 10);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));

        assertEquals(true, service.hasFeatureAccess("user@example.com", SubscriptionFeature.AD_FREE));
    }

    @Test
    void hasFeatureAccess_whenHealthIntegrationOnFreePlan_returnsFalse() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.FREE, SubscriptionStatus.ACTIVE, 3, 0);

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
        when(userSubscriptionEntitlementRepository.countBySubscription(entity)).thenReturn(1L);
        when(userSubscriptionEntitlementRepository.existsActiveFeature(eq(7L), eq(SubscriptionFeature.HEALTH_INTEGRATION), any()))
                .thenReturn(false);

        assertEquals(false, service.hasFeatureAccess("user@example.com", SubscriptionFeature.HEALTH_INTEGRATION));
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
    void assertFeatureAccess_whenFeatureDenied_throwsIllegalArgumentException() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.FREE, SubscriptionStatus.ACTIVE, 3, 1);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));

        assertThrows(IllegalArgumentException.class,
                () -> service.assertFeatureAccess("user@example.com", SubscriptionFeature.AD_FREE));
    }

    @Test
    void consumeAiQuota_whenQuotaAvailable_incrementsUsage() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE, 15, 14);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionDto result = service.consumeAiQuota("user@example.com");

        assertEquals(15, result.getAiUsedThisPeriod());
        assertEquals(0, result.getAiRemainingThisPeriod());
        assertEquals(false, result.getAiAccessAllowed());
        assertEquals(true, result.getUpgradeRecommended());
    }

    @Test
    void consumeAiQuota_whenPeriodExpired_resetsUsageBeforeConsume() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE, 15, 15);
        entity.setAiQuotaPeriodStartDate(java.time.LocalDate.now().minusMonths(1));
        entity.setAiQuotaPeriodEndDate(java.time.LocalDate.now().minusDays(1));

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
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

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionDto result = service.consumeAiQuota("user@example.com");

        assertEquals(101, result.getAiUsedThisPeriod());
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
        assertEquals(java.time.LocalDate.now().plusDays(6), result.getAiAddonQuotaExpiresAt());
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
    void consumeAiQuota_whenBasePeriodResets_keepsUnexpiredOneOffAddonQuota() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE, 100, 100);
        entity.setAiAddonQuota(15);
        entity.setAiAddonQuotaExpiresAt(java.time.LocalDate.now().plusDays(5));
        entity.setAiQuotaPeriodStartDate(java.time.LocalDate.now().minusMonths(1));
        entity.setAiQuotaPeriodEndDate(java.time.LocalDate.now().minusDays(1));

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(entity));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionDto result = service.consumeAiQuota("user@example.com");

        assertEquals(15, result.getAiAddonQuota());
        assertEquals(java.time.LocalDate.now().plusDays(5), result.getAiAddonQuotaExpiresAt());
        assertEquals(1, result.getAiUsedThisPeriod());
        assertEquals(114, result.getAiRemainingThisPeriod());
    }

    @Test
    void consumeAiQuota_whenQuotaUnavailable_throwsIllegalArgumentException() {
        SubscriptionEntity entity = subscription(SubscriptionPlan.FREE, SubscriptionStatus.ACTIVE, 3, 3);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
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

    private SubscriptionEntity subscription(SubscriptionPlan plan, SubscriptionStatus status, int quota, int used) {
        SubscriptionEntity entity = new SubscriptionEntity();
        entity.setUser(user);
        entity.setPlanType(plan);
        entity.setStatus(status);
        entity.setBillingPeriod(BillingPeriod.MONTHLY);
        entity.setStartDate(java.time.LocalDate.of(2026, 5, 1));
        entity.setAiQuotaPeriodStartDate(java.time.LocalDate.of(2026, 5, 1));
        entity.setAiQuotaPeriodEndDate(java.time.LocalDate.of(2026, 5, 31));
        entity.setAiMonthlyQuota(quota);
        entity.setAiAddonQuota(0);
        entity.setAiUsedThisPeriod(used);
        entity.setAutoRenew(true);
        return entity;
    }
}
