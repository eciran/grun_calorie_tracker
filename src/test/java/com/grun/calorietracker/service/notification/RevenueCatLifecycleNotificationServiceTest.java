package com.grun.calorietracker.service.notification;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.NotificationEventType;
import com.grun.calorietracker.enums.NotificationOccurrenceStatus;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.RevenueCatEventType;
import com.grun.calorietracker.enums.SubscriptionPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RevenueCatLifecycleNotificationServiceTest {
    private NotificationOrchestrationService orchestrationService;
    private RevenueCatLifecycleNotificationService service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        orchestrationService = mock(NotificationOrchestrationService.class);
        when(orchestrationService.enqueue(any())).thenReturn(new NotificationOrchestrationResult(
                1L, 2L, 3L, NotificationOccurrenceStatus.QUEUED, "ACCOUNT_TRANSACTIONAL", false));
        service = new RevenueCatLifecycleNotificationService(orchestrationService);
        user = new UserEntity();
        user.setId(42L);
        user.setPreferredLanguage(PreferredLanguage.EN);
    }

    @ParameterizedTest
    @CsvSource({
            "INITIAL_PURCHASE,SUBSCRIPTION_STARTED",
            "RENEWAL,SUBSCRIPTION_RENEWED",
            "UNCANCELLATION,SUBSCRIPTION_RESUMED",
            "CANCELLATION,SUBSCRIPTION_CANCELLED",
            "EXPIRATION,SUBSCRIPTION_EXPIRED",
            "BILLING_ISSUE,SUBSCRIPTION_BILLING_ISSUE",
            "PRODUCT_CHANGE,SUBSCRIPTION_PLAN_CHANGED",
            "SUBSCRIPTION_PAUSED,SUBSCRIPTION_PAUSED"
    })
    void enqueue_mapsSubscriptionLifecycleToTypedNotification(
            RevenueCatEventType providerType, NotificationEventType expectedType) {
        service.enqueue(user, providerType, "provider-event-1", SubscriptionPlan.PRO,
                LocalDate.of(2026, 9, 30), null, null, false);

        ArgumentCaptor<NotificationOrchestrationRequest> request =
                ArgumentCaptor.forClass(NotificationOrchestrationRequest.class);
        verify(orchestrationService).enqueue(request.capture());
        assertThat(request.getValue().eventType()).isEqualTo(expectedType);
        assertThat(request.getValue().source()).isEqualTo("REVENUECAT");
        assertThat(request.getValue().sourceEventId()).isEqualTo("provider-event-1");
        assertThat(request.getValue().parameters()).containsEntry("planName", "Pro");
        assertThat(request.getValue().targetRoute()).isEqualTo("manage-subscription");
    }

    @Test
    void enqueue_localizesTurkishDatesWithoutExposingProviderIdentifiers() {
        user.setPreferredLanguage(PreferredLanguage.TR);

        service.enqueue(user, RevenueCatEventType.RENEWAL, "private-provider-event",
                SubscriptionPlan.PLUS, LocalDate.of(2026, 9, 30), null, null, false);

        ArgumentCaptor<NotificationOrchestrationRequest> request =
                ArgumentCaptor.forClass(NotificationOrchestrationRequest.class);
        verify(orchestrationService).enqueue(request.capture());
        assertThat(request.getValue().parameters())
                .containsEntry("planName", "Plus")
                .containsEntry("periodEndDate", "30 Eylül 2026")
                .doesNotContainKeys("transactionId", "providerEventId", "price");
        assertThat(request.getValue().title()).isEqualTo("Aboneliğin yenilendi");
    }

    @Test
    void enqueue_mapsVerifiedAiAddonPurchaseWithAppliedValidity() {
        service.enqueue(user, RevenueCatEventType.NON_RENEWING_PURCHASE, "addon-event",
                SubscriptionPlan.PRO, null, 50, LocalDate.of(2026, 10, 1), false);

        ArgumentCaptor<NotificationOrchestrationRequest> request =
                ArgumentCaptor.forClass(NotificationOrchestrationRequest.class);
        verify(orchestrationService).enqueue(request.capture());
        assertThat(request.getValue().eventType()).isEqualTo(NotificationEventType.AI_ADDON_PURCHASED);
        assertThat(request.getValue().parameters())
                .containsEntry("creditAmount", "50")
                .containsEntry("validUntilDate", "1 October 2026");
        assertThat(request.getValue().targetRoute()).isEqualTo("ai-credits");
    }

    @Test
    void enqueue_mapsSubscriptionRefundButSkipsAddonRefundAndTransfer() {
        service.enqueue(user, RevenueCatEventType.CANCELLATION, "refund-event",
                SubscriptionPlan.PRO, LocalDate.of(2026, 9, 1), null, null, true);

        ArgumentCaptor<NotificationOrchestrationRequest> request =
                ArgumentCaptor.forClass(NotificationOrchestrationRequest.class);
        verify(orchestrationService).enqueue(request.capture());
        assertThat(request.getValue().eventType()).isEqualTo(NotificationEventType.SUBSCRIPTION_REFUNDED);

        orchestrationService = mock(NotificationOrchestrationService.class);
        service = new RevenueCatLifecycleNotificationService(orchestrationService);
        assertThat(service.enqueue(user, RevenueCatEventType.CANCELLATION, "addon-refund",
                SubscriptionPlan.PRO, LocalDate.now(), 15, null, true)).isEmpty();
        assertThat(service.enqueue(user, RevenueCatEventType.TRANSFER, "transfer",
                SubscriptionPlan.PRO, LocalDate.now(), null, null, false)).isEmpty();
        verify(orchestrationService, never()).enqueue(any());
    }

    @Test
    void enqueue_failsClosedWhenARequiredLifecycleDateIsMissing() {
        assertThatThrownBy(() -> service.enqueue(user, RevenueCatEventType.RENEWAL, "renewal",
                SubscriptionPlan.PRO, null, null, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lifecycle date");
        verify(orchestrationService, never()).enqueue(any());
    }
}
