package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.RevenueCatProperties;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.dto.SubscriptionProviderEventCommand;
import com.grun.calorietracker.dto.PromoProviderRedemptionCommand;
import com.grun.calorietracker.entity.SubscriptionEntity;
import com.grun.calorietracker.entity.SubscriptionProviderEventEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.PaymentProvider;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import com.grun.calorietracker.enums.SubscriptionStatus;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.SubscriptionProviderEventRepository;
import com.grun.calorietracker.repository.SubscriptionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.RevenueCatWebhookServiceImpl;
import com.grun.calorietracker.service.notification.RevenueCatLifecycleNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RevenueCatWebhookServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionProviderEventRepository eventRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private PromoProviderRedemptionService promoProviderRedemptionService;

    @Mock
    private RevenueCatLifecycleNotificationService lifecycleNotificationService;

    @Mock
    private StoreSubscriptionOwnershipService ownershipService;

    private RevenueCatWebhookServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private UserEntity user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        RevenueCatProperties properties = new RevenueCatProperties();
        properties.setWebhookAuthorization("Bearer rc-secret");
        properties.getProducts().setPro(List.of("grun_pro_monthly", "grun_pro_yearly"));
        properties.getProducts().setPlus(List.of("grun_plus_monthly", "grun_plus_yearly"));
        properties.getProducts().getAiAddonQuotas().put("grun_ai_15_credits", 15);
        properties.getProducts().getAiAddonQuotas().put("grun_ai_50_credits", 50);
        properties.getProducts().getAiAddonValidityDays().put("grun_ai_15_credits", 30);
        properties.getProducts().getAiAddonValidityDays().put("grun_ai_50_credits", 30);
        service = new RevenueCatWebhookServiceImpl(properties, objectMapper, userRepository, eventRepository,
                notificationRepository, subscriptionRepository, subscriptionService, promoProviderRedemptionService,
                lifecycleNotificationService, ownershipService);
        user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.empty());
    }

    @Test
    void processWebhook_whenInitialPurchase_appliesProSubscription() throws Exception {
        String payload = """
                {
                  "event": {
                    "id": "evt_1",
                    "type": "INITIAL_PURCHASE",
                    "app_user_id": "user:1",
                    "product_id": "grun_pro_monthly",
                    "entitlement_ids": ["pro"],
                    "transaction_id": "tx_1",
                    "original_transaction_id": "otx_1",
                    "event_timestamp_ms": 1771950000000,
                    "purchased_at_ms": 1771950000000,
                    "expiration_at_ms": 1774628400000,
                    "period_type": "NORMAL"
                  }
                }
                """;
        when(eventRepository.findByProviderAndProviderEventId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.applyProviderEvent(any(), any())).thenReturn(new SubscriptionDto());
        when(eventRepository.save(any(SubscriptionProviderEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.processWebhook("Bearer rc-secret", objectMapper.readTree(payload));

        assertEquals("PROCESSED", result.getStatus());
        ArgumentCaptor<SubscriptionProviderEventCommand> captor = ArgumentCaptor.forClass(SubscriptionProviderEventCommand.class);
        verify(subscriptionService).applyProviderEvent(org.mockito.ArgumentMatchers.eq(1L), captor.capture());
        assertEquals(SubscriptionPlan.PRO, captor.getValue().getPlanType());
        assertEquals(SubscriptionStatus.ACTIVE, captor.getValue().getStatus());
        assertEquals("grun_pro_monthly", captor.getValue().getProviderProductId());
        assertEquals(true, captor.getValue().getGrantPlanCreditAllocation());
        assertNotNull(captor.getValue().getPurchasedAt());
        assertNotNull(captor.getValue().getProviderEventAt());
        assertEquals(64, captor.getValue().getCreditAllocationKey().length());
        verify(eventRepository).save(any(SubscriptionProviderEventEntity.class));
        verify(promoProviderRedemptionService).recordVerifiedPurchase(any());
        verify(lifecycleNotificationService).enqueue(
                org.mockito.ArgumentMatchers.eq(user),
                org.mockito.ArgumentMatchers.eq(com.grun.calorietracker.enums.RevenueCatEventType.INITIAL_PURCHASE),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(SubscriptionPlan.PRO),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void processWebhook_whenDuplicateEvent_skipsProcessing() throws Exception {
        String payload = """
                {"event":{"id":"evt_1","type":"RENEWAL","app_user_id":"user:1","product_id":"grun_pro_monthly","event_timestamp_ms":1771950000000}}
                """;
        SubscriptionProviderEventEntity existing = new SubscriptionProviderEventEntity();
        existing.setStatus(SubscriptionProviderEventStatus.PROCESSED);
        when(eventRepository.findByProviderAndProviderEventId(any(), any())).thenReturn(Optional.of(existing));

        var result = service.processWebhook("Bearer rc-secret", objectMapper.readTree(payload));

        assertEquals(true, result.getDuplicate());
        assertEquals("IGNORED", result.getStatus());
        verify(subscriptionService, never()).applyProviderEvent(any(), any());
        verify(lifecycleNotificationService, never()).enqueue(any(), any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void processWebhook_whenAddonPurchase_grantsOneOffQuota() throws Exception {
        String payload = """
                {
                  "event": {
                    "id": "evt_addon",
                    "type": "NON_RENEWING_PURCHASE",
                    "app_user_id": "user:1",
                    "product_id": "grun_ai_15_credits",
                    "transaction_id": "tx_addon",
                    "event_timestamp_ms": 1771950000000
                  }
                }
                """;
        when(eventRepository.findByProviderAndProviderEventId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.applyProviderEvent(any(), any())).thenReturn(new SubscriptionDto());
        when(eventRepository.save(any(SubscriptionProviderEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.processWebhook("Bearer rc-secret", objectMapper.readTree(payload));

        ArgumentCaptor<SubscriptionProviderEventCommand> captor = ArgumentCaptor.forClass(SubscriptionProviderEventCommand.class);
        verify(subscriptionService).applyProviderEvent(org.mockito.ArgumentMatchers.eq(1L), captor.capture());
        assertEquals(15, captor.getValue().getAiAddonQuotaAmount());
        assertEquals(30, captor.getValue().getAiAddonValidityDays());
    }

    @Test
    void processWebhook_whenFiftyCreditAddonPurchased_grantsConfiguredQuota() throws Exception {
        String payload = """
                {"event":{"id":"evt_addon_50","type":"NON_RENEWING_PURCHASE","app_user_id":"user:1","product_id":"grun_ai_50_credits","transaction_id":"tx_addon_50","event_timestamp_ms":1771950000000}}
                """;
        when(eventRepository.findByProviderAndProviderEventId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.applyProviderEvent(any(), any())).thenReturn(new SubscriptionDto());
        when(eventRepository.save(any(SubscriptionProviderEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.processWebhook("Bearer rc-secret", objectMapper.readTree(payload));

        ArgumentCaptor<SubscriptionProviderEventCommand> captor = ArgumentCaptor.forClass(SubscriptionProviderEventCommand.class);
        verify(subscriptionService).applyProviderEvent(org.mockito.ArgumentMatchers.eq(1L), captor.capture());
        assertEquals(50, captor.getValue().getAiAddonQuotaAmount());
        assertEquals(30, captor.getValue().getAiAddonValidityDays());
    }

    @Test
    void processWebhook_whenCustomerSupportCancellation_marksSubscriptionRefunded() throws Exception {
        SubscriptionEntity activeSubscription = new SubscriptionEntity();
        activeSubscription.setPlanType(SubscriptionPlan.PRO);
        activeSubscription.setStatus(SubscriptionStatus.ACTIVE);
        activeSubscription.setAiMonthlyQuota(200);
        activeSubscription.setAiPlanUsedThisPeriod(37);
        activeSubscription.setAiAddonQuota(15);
        activeSubscription.setAiAddonUsed(4);
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(activeSubscription));
        String payload = """
                {
                  "event": {
                    "id": "evt_refund",
                    "type": "CANCELLATION",
                    "app_user_id": "user:1",
                    "product_id": "grun_pro_monthly",
                    "entitlement_ids": ["pro"],
                    "transaction_id": "tx_refund",
                    "original_transaction_id": "otx_refund",
                    "event_timestamp_ms": 1771950000000,
                    "expiration_at_ms": 1771950000000,
                    "cancel_reason": "CUSTOMER_SUPPORT",
                    "environment": "SANDBOX",
                    "store": "APP_STORE"
                  }
                }
                """;
        when(eventRepository.findByProviderAndProviderEventId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.applyProviderEvent(any(), any())).thenReturn(new SubscriptionDto());
        when(eventRepository.save(any(SubscriptionProviderEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.processWebhook("Bearer rc-secret", objectMapper.readTree(payload));

        ArgumentCaptor<SubscriptionProviderEventCommand> captor = ArgumentCaptor.forClass(SubscriptionProviderEventCommand.class);
        verify(subscriptionService).applyProviderEvent(org.mockito.ArgumentMatchers.eq(1L), captor.capture());
        assertEquals(true, captor.getValue().getRefund());
        assertEquals("grun_pro_monthly", captor.getValue().getProviderProductId());
        ArgumentCaptor<SubscriptionProviderEventEntity> auditCaptor = ArgumentCaptor.forClass(SubscriptionProviderEventEntity.class);
        verify(eventRepository).save(auditCaptor.capture());
        assertEquals("SANDBOX", auditCaptor.getValue().getEnvironment());
        assertEquals("CUSTOMER_SUPPORT", auditCaptor.getValue().getCancelReason());
        assertEquals(true, auditCaptor.getValue().getEntitlementDeliveredSnapshot());
        assertEquals(true, auditCaptor.getValue().getEntitlementActiveSnapshot());
        assertEquals(200, auditCaptor.getValue().getPlanQuotaSnapshot());
        assertEquals(37, auditCaptor.getValue().getPlanUsedSnapshot());
        assertEquals(15, auditCaptor.getValue().getAddonQuotaSnapshot());
        assertEquals(4, auditCaptor.getValue().getAddonUsedSnapshot());
    }

    @Test
    void processWebhook_whenRefundIsReversed_restoresMappedSubscription() throws Exception {
        String payload = """
                {"event":{"id":"evt_refund_reversed","type":"REFUND_REVERSED","app_user_id":"user:1",
                "product_id":"grun_pro_monthly","entitlement_ids":["pro"],"transaction_id":"tx_refund",
                "original_transaction_id":"otx_refund","event_timestamp_ms":1772036400000,
                "purchased_at_ms":1771950000000,"expiration_at_ms":1774455600000,
                "period_type":"NORMAL","environment":"PRODUCTION","store":"APP_STORE"}}
                """;
        when(eventRepository.findByProviderAndProviderEventId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.applyProviderEvent(any(), any())).thenReturn(new SubscriptionDto());
        when(eventRepository.save(any(SubscriptionProviderEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.processWebhook("Bearer rc-secret", objectMapper.readTree(payload));

        ArgumentCaptor<SubscriptionProviderEventCommand> captor = ArgumentCaptor.forClass(SubscriptionProviderEventCommand.class);
        verify(subscriptionService).applyProviderEvent(org.mockito.ArgumentMatchers.eq(1L), captor.capture());
        assertEquals(com.grun.calorietracker.enums.RevenueCatEventType.REFUND_REVERSED, captor.getValue().getEventType());
        assertEquals(SubscriptionPlan.PRO, captor.getValue().getPlanType());
        assertEquals(SubscriptionStatus.ACTIVE, captor.getValue().getStatus());
        assertEquals(false, captor.getValue().getGrantPlanCreditAllocation());
    }

    @Test
    void processWebhook_whenAuthorizationInvalid_throwsAccessDenied() throws Exception {
        String payload = """
                {"event":{"id":"evt_1","type":"RENEWAL","app_user_id":"user:1","product_id":"grun_pro_monthly","event_timestamp_ms":1771950000000}}
                """;

        assertThrows(AccessDeniedException.class,
                () -> service.processWebhook("Bearer wrong", objectMapper.readTree(payload)));
    }

    @Test
    void processWebhook_whenAuthorizationNotConfigured_throwsAccessDenied() throws Exception {
        RevenueCatProperties properties = new RevenueCatProperties();
        RevenueCatWebhookServiceImpl unsecuredService =
                new RevenueCatWebhookServiceImpl(properties, objectMapper, userRepository, eventRepository,
                        notificationRepository, subscriptionRepository, subscriptionService,
                        promoProviderRedemptionService, lifecycleNotificationService, ownershipService);
        String payload = """
                {"event":{"id":"evt_1","type":"RENEWAL","app_user_id":"user:1","product_id":"grun_pro_monthly","event_timestamp_ms":1771950000000}}
                """;

        assertThrows(AccessDeniedException.class,
                () -> unsecuredService.processWebhook("Bearer rc-secret", objectMapper.readTree(payload)));
        verify(subscriptionService, never()).applyProviderEvent(any(), any());
    }

    @Test
    void processWebhook_whenUserCannotBeResolved_storesFailedEvent() throws Exception {
        String payload = """
                {"event":{"id":"evt_404","type":"RENEWAL","app_user_id":"user:404","product_id":"grun_pro_monthly","event_timestamp_ms":1771950000000}}
                """;
        when(eventRepository.findByProviderAndProviderEventId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(404L)).thenReturn(Optional.empty());
        when(eventRepository.save(any(SubscriptionProviderEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.processWebhook("Bearer rc-secret", objectMapper.readTree(payload));

        assertEquals("FAILED", result.getStatus());
        ArgumentCaptor<SubscriptionProviderEventEntity> captor = ArgumentCaptor.forClass(SubscriptionProviderEventEntity.class);
        verify(eventRepository).save(captor.capture());
        assertEquals(SubscriptionProviderEventStatus.FAILED, captor.getValue().getStatus());
        verify(subscriptionService, never()).applyProviderEvent(any(), any());
    }

    @Test
    void processWebhook_storeOfferCode_isAuditedAndPassedToPromotionAttribution() throws Exception {
        String payload = """
                {"event":{"id":"evt_offer","type":"INITIAL_PURCHASE","app_user_id":"user:1",
                "product_id":"grun_pro_monthly","transaction_id":"tx_offer","original_transaction_id":"otx_offer",
                "purchased_at_ms":1771950000000,"expiration_at_ms":1774628400000,"event_timestamp_ms":1771950000000,
                "environment":"SANDBOX","store":"APP_STORE","offer_code":"PARTNER15"}}
                """;
        when(eventRepository.findByProviderAndProviderEventId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.applyProviderEvent(any(), any())).thenReturn(new SubscriptionDto());
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.processWebhook("Bearer rc-secret", objectMapper.readTree(payload));

        ArgumentCaptor<PromoProviderRedemptionCommand> promo = ArgumentCaptor.forClass(PromoProviderRedemptionCommand.class);
        verify(promoProviderRedemptionService).recordVerifiedPurchase(promo.capture());
        assertEquals("PARTNER15", promo.getValue().offerCode());
        ArgumentCaptor<SubscriptionProviderEventEntity> audit = ArgumentCaptor.forClass(SubscriptionProviderEventEntity.class);
        verify(eventRepository, org.mockito.Mockito.atLeastOnce()).save(audit.capture());
        assertEquals("PARTNER15", audit.getAllValues().get(0).getStoreOfferCode());
    }

    @Test
    void processWebhook_samePurchaseWithDifferentEventIdsProducesSameAllocationIdentity() throws Exception {
        when(eventRepository.findByProviderAndProviderEventId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.applyProviderEvent(any(), any())).thenReturn(new SubscriptionDto());
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        String template = """
                {"event":{"id":"%s","type":"INITIAL_PURCHASE","app_user_id":"user:1",
                "product_id":"grun_pro_monthly","transaction_id":"tx_same","original_transaction_id":"otx_same",
                "purchased_at_ms":1771950000000,"expiration_at_ms":1774628400000,"event_timestamp_ms":%d,
                "environment":"SANDBOX","store":"APP_STORE"}}
                """;

        service.processWebhook("Bearer rc-secret", objectMapper.readTree(template.formatted("evt_a", 1771950000100L)));
        service.processWebhook("Bearer rc-secret", objectMapper.readTree(template.formatted("evt_b", 1771950000200L)));

        ArgumentCaptor<SubscriptionProviderEventCommand> captor = ArgumentCaptor.forClass(SubscriptionProviderEventCommand.class);
        verify(subscriptionService, org.mockito.Mockito.times(2)).applyProviderEvent(org.mockito.ArgumentMatchers.eq(1L), captor.capture());
        assertEquals(captor.getAllValues().get(0).getCreditAllocationKey(), captor.getAllValues().get(1).getCreditAllocationKey());
        assertFalse(captor.getAllValues().get(0).getProviderEventId().equals(captor.getAllValues().get(1).getProviderEventId()));
    }

    @Test
    void processWebhook_uncancellationReenablesWithoutNewCreditAllocation() throws Exception {
        String payload = """
                {"event":{"id":"evt_uncancel","type":"UNCANCELLATION","app_user_id":"user:1",
                "product_id":"grun_plus_monthly","transaction_id":"tx_1","original_transaction_id":"otx_1",
                "purchased_at_ms":1771950000000,"expiration_at_ms":1774628400000,"event_timestamp_ms":1771950001000}}
                """;
        when(eventRepository.findByProviderAndProviderEventId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.applyProviderEvent(any(), any())).thenReturn(new SubscriptionDto());
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.processWebhook("Bearer rc-secret", objectMapper.readTree(payload));

        ArgumentCaptor<SubscriptionProviderEventCommand> captor = ArgumentCaptor.forClass(SubscriptionProviderEventCommand.class);
        verify(subscriptionService).applyProviderEvent(org.mockito.ArgumentMatchers.eq(1L), captor.capture());
        assertFalse(Boolean.TRUE.equals(captor.getValue().getGrantPlanCreditAllocation()));
        assertEquals(null, captor.getValue().getCreditAllocationKey());
    }

    @Test
    void processWebhook_productChangeIsOnlyAuditedUntilEffectivePurchaseEventArrives() throws Exception {
        String payload = """
                {"event":{"id":"evt_change","type":"PRODUCT_CHANGE","app_user_id":"user:1",
                "original_app_user_id":"$RCAnonymousID:original-device-id",
                "aliases":["user:1","$RCAnonymousID:original-device-id"],
                "product_id":"grun_plus_monthly","new_product_id":"grun_pro_monthly",
                "transaction_id":"tx_change","event_timestamp_ms":1771950000000}}
                """;
        when(eventRepository.findByProviderAndProviderEventId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.processWebhook("Bearer rc-secret", objectMapper.readTree(payload));

        assertEquals("PROCESSED", result.getStatus());
        verify(subscriptionService, never()).applyProviderEvent(any(), any());
        verify(lifecycleNotificationService).enqueue(
                org.mockito.ArgumentMatchers.eq(user),
                org.mockito.ArgumentMatchers.eq(com.grun.calorietracker.enums.RevenueCatEventType.PRODUCT_CHANGE),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(SubscriptionPlan.PRO),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(false));
        ArgumentCaptor<SubscriptionProviderEventEntity> audit = ArgumentCaptor.forClass(SubscriptionProviderEventEntity.class);
        verify(eventRepository).save(audit.capture());
        assertEquals("grun_pro_monthly", audit.getValue().getNewProductId());
    }

    @Test
    void processWebhook_subscriptionPauseNotifiesWithoutEndingCurrentEntitlement() throws Exception {
        String payload = """
                {"event":{"id":"evt_pause","type":"SUBSCRIPTION_PAUSED","app_user_id":"user:1",
                "product_id":"grun_pro_monthly","expiration_at_ms":1788134400000,
                "event_timestamp_ms":1785456000000}}
                """;
        SubscriptionEntity current = new SubscriptionEntity();
        current.setPlanType(SubscriptionPlan.PRO);
        current.setStatus(SubscriptionStatus.ACTIVE);
        when(eventRepository.findByProviderAndProviderEventId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(current));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.processWebhook("Bearer rc-secret", objectMapper.readTree(payload));

        assertEquals("PROCESSED", result.getStatus());
        verify(subscriptionService, never()).applyProviderEvent(any(), any());
        verify(lifecycleNotificationService).enqueue(
                org.mockito.ArgumentMatchers.eq(user),
                org.mockito.ArgumentMatchers.eq(com.grun.calorietracker.enums.RevenueCatEventType.SUBSCRIPTION_PAUSED),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(SubscriptionPlan.PRO),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void processWebhook_crossAccountTransferRequiresReviewWithoutGrantingEntitlements() throws Exception {
        String payload = """
                {"event":{"id":"evt_transfer","type":"TRANSFER",
                "transferred_from":["user:2","$RCAnonymousID:source-device-id"],
                "transferred_to":["user:1","$RCAnonymousID:destination-device-id"],
                "event_timestamp_ms":1785456000000}}
                """;
        when(eventRepository.findByProviderAndProviderEventId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.processWebhook("Bearer rc-secret", objectMapper.readTree(payload));

        assertEquals("FAILED", result.getStatus());
        verify(subscriptionService, never()).applyProviderEvent(any(), any());
        verify(lifecycleNotificationService, never()).enqueue(any(), any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean());
        ArgumentCaptor<SubscriptionProviderEventEntity> audit = ArgumentCaptor.forClass(SubscriptionProviderEventEntity.class);
        verify(eventRepository).save(audit.capture());
        assertEquals(user, audit.getValue().getUser());
        assertEquals("user:1", audit.getValue().getProviderAppUserId());
        assertEquals(SubscriptionProviderEventStatus.FAILED, audit.getValue().getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(audit.getValue().getProcessingError().startsWith("SUBSCRIPTION_OWNERSHIP_CONFLICT"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"user:1", "$RCAnonymousID:device"})
    void processWebhook_sameAccountOrAnonymousTransferIsNotClassifiedAsCrossAccount(String source) throws Exception {
        String payload = """
                {"event":{"id":"evt_same_owner_transfer","type":"TRANSFER",
                "transferred_from":["%s"],"transferred_to":["user:1"],
                "event_timestamp_ms":1785456000000}}
                """.formatted(source);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.processWebhook("Bearer rc-secret", objectMapper.readTree(payload));
        assertEquals("IGNORED", result.getStatus());
        verify(subscriptionService, never()).applyProviderEvent(any(), any());
    }

    @Test
    void processWebhook_whenCurrentIdIsAnonymous_resolvesSingleBackendAlias() throws Exception {
        String payload = """
                {"event":{"id":"evt_alias","type":"PRODUCT_CHANGE",
                "app_user_id":"$RCAnonymousID:current-device-id",
                "original_app_user_id":"$RCAnonymousID:original-device-id",
                "aliases":["$RCAnonymousID:current-device-id","user:1"],
                "product_id":"grun_plus_monthly","new_product_id":"grun_pro_monthly",
                "event_timestamp_ms":1771950000000}}
                """;
        when(eventRepository.findByProviderAndProviderEventId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.processWebhook("Bearer rc-secret", objectMapper.readTree(payload));

        assertEquals("PROCESSED", result.getStatus());
        verify(lifecycleNotificationService).enqueue(
                org.mockito.ArgumentMatchers.eq(user),
                org.mockito.ArgumentMatchers.eq(com.grun.calorietracker.enums.RevenueCatEventType.PRODUCT_CHANGE),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(SubscriptionPlan.PRO),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void processWebhook_purchaseWithoutStablePeriodIdentityFailsClosed() throws Exception {
        String payload = """
                {"event":{"id":"evt_incomplete","type":"INITIAL_PURCHASE","app_user_id":"user:1",
                "product_id":"grun_pro_monthly","event_timestamp_ms":1771950000000}}
                """;
        when(eventRepository.findByProviderAndProviderEventId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.processWebhook("Bearer rc-secret", objectMapper.readTree(payload));

        assertEquals("FAILED", result.getStatus());
        verify(subscriptionService, never()).applyProviderEvent(any(), any());
    }

    @Test
    void processWebhook_whenAppUserIdIsEmail_storesFailedEvent() throws Exception {
        String payload = """
                {"event":{"id":"evt_email","type":"RENEWAL","app_user_id":"user@example.com","product_id":"grun_pro_monthly","event_timestamp_ms":1771950000000}}
                """;
        when(eventRepository.findByProviderAndProviderEventId(any(), any())).thenReturn(Optional.empty());
        when(eventRepository.save(any(SubscriptionProviderEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.processWebhook("Bearer rc-secret", objectMapper.readTree(payload));

        assertEquals("FAILED", result.getStatus());
        verify(userRepository, never()).findByEmail(any());
        verify(subscriptionService, never()).applyProviderEvent(any(), any());
    }

    @Test
    void processWebhook_whenExistingRevenueCatCustomerDiffers_storesFailedEvent() throws Exception {
        String payload = """
                {"event":{"id":"evt_customer_mismatch","type":"RENEWAL","app_user_id":"user:1","original_app_user_id":"user:1","product_id":"grun_pro_monthly","event_timestamp_ms":1771950000000}}
                """;
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setProvider(PaymentProvider.REVENUECAT);
        subscription.setProviderCustomerId("user:999");
        when(eventRepository.findByProviderAndProviderEventId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(subscription));
        when(eventRepository.save(any(SubscriptionProviderEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.processWebhook("Bearer rc-secret", objectMapper.readTree(payload));

        assertEquals("FAILED", result.getStatus());
        verify(subscriptionService, never()).applyProviderEvent(any(), any());
    }

    @Test
    void processWebhook_whenStrictMappingAndProductUnknown_storesFailedEvent() throws Exception {
        String payload = """
                {"event":{"id":"evt_unknown","type":"INITIAL_PURCHASE","app_user_id":"user:1","product_id":"unknown_plan","event_timestamp_ms":1771950000000}}
                """;
        when(eventRepository.findByProviderAndProviderEventId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(eventRepository.save(any(SubscriptionProviderEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.processWebhook("Bearer rc-secret", objectMapper.readTree(payload));

        assertEquals("FAILED", result.getStatus());
        verify(subscriptionService, never()).applyProviderEvent(any(), any());
    }
}
