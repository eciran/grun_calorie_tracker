package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.RevenueCatProperties;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.dto.SubscriptionProviderEventCommand;
import com.grun.calorietracker.entity.SubscriptionProviderEventEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import com.grun.calorietracker.enums.SubscriptionStatus;
import com.grun.calorietracker.repository.SubscriptionProviderEventRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.RevenueCatWebhookServiceImpl;
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

class RevenueCatWebhookServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionProviderEventRepository eventRepository;

    @Mock
    private SubscriptionService subscriptionService;

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
        properties.getProducts().getAiAddonValidityDays().put("grun_ai_15_credits", 30);
        service = new RevenueCatWebhookServiceImpl(properties, objectMapper, userRepository, eventRepository, subscriptionService);
        user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");
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
        verify(eventRepository).save(any(SubscriptionProviderEventEntity.class));
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
        verify(eventRepository, never()).save(any());
    }

    @Test
    void processWebhook_whenAddonPurchase_grantsOneOffQuota() throws Exception {
        String payload = """
                {
                  "event": {
                    "id": "evt_addon",
                    "type": "NON_RENEWING_PURCHASE",
                    "app_user_id": "1",
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
    void processWebhook_whenCustomerSupportCancellation_marksSubscriptionRefunded() throws Exception {
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
    void processWebhook_whenUserCannotBeResolved_storesFailedEvent() throws Exception {
        String payload = """
                {"event":{"id":"evt_404","type":"RENEWAL","app_user_id":"missing@example.com","product_id":"grun_pro_monthly","event_timestamp_ms":1771950000000}}
                """;
        when(eventRepository.findByProviderAndProviderEventId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        when(eventRepository.save(any(SubscriptionProviderEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.processWebhook("Bearer rc-secret", objectMapper.readTree(payload));

        assertEquals("FAILED", result.getStatus());
        ArgumentCaptor<SubscriptionProviderEventEntity> captor = ArgumentCaptor.forClass(SubscriptionProviderEventEntity.class);
        verify(eventRepository).save(captor.capture());
        assertEquals(SubscriptionProviderEventStatus.FAILED, captor.getValue().getStatus());
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
