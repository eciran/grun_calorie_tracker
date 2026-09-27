package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.grun.calorietracker.config.RevenueCatProperties;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.entity.SubscriptionVerificationEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.PaymentProvider;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.repository.SubscriptionVerificationRepository;
import com.grun.calorietracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SubscriptionPurchaseVerificationServiceTest {
    final UserRepository users = mock(UserRepository.class);
    final SubscriptionVerificationRepository states = mock(SubscriptionVerificationRepository.class);
    final RevenueCatPurchaseEvidenceClient provider = mock(RevenueCatPurchaseEvidenceClient.class);
    final SubscriptionService subscriptions = mock(SubscriptionService.class);
    final RevenueCatWebhookService webhooks = mock(RevenueCatWebhookService.class);
    final RevenueCatProperties properties = new RevenueCatProperties();
    SubscriptionPurchaseVerificationService service;
    SubscriptionVerificationEntity state;
    SubscriptionDto current;
    final Instant purchased = Instant.now().minusSeconds(30).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

    @BeforeEach void setup() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        properties.getProducts().setPro(List.of("grun_pro_monthly"));
        properties.setWebhookAuthorization("Bearer server-only");
        service = new SubscriptionPurchaseVerificationService(users, states, provider, properties, subscriptions, webhooks, manager);
        var user = new UserEntity(); user.setId(44L); user.setEmail("test@example.com");
        when(users.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(44L)).thenReturn(Optional.of(user));
        when(states.findById(44L)).thenAnswer(i -> Optional.ofNullable(state));
        when(states.saveAndFlush(any())).thenAnswer(i -> { state = i.getArgument(0); return state; });
        current = new SubscriptionDto();
        current.setPlanType(SubscriptionPlan.FREE);
        when(subscriptions.getCurrentSubscription("test@example.com")).thenAnswer(i -> current);
    }

    @Test void requestsAreDurableIdempotentAndDoNotCallProviderInline() {
        request(); request();
        assertThat(state.getAttempts()).isZero();
        assertThat(state.getNextAttemptAt()).isNotNull();
        verifyNoInteractions(provider, webhooks);
        verify(states, times(1)).saveAndFlush(any());
    }

    @Test void noEvidenceDoesNotValidateManualProAndSchedulesRetry() {
        makePaid(); current.setProvider(PaymentProvider.MANUAL_ADMIN);
        request(); service.verifyOne(44L);
        assertThat(state.getStatus()).isEqualTo("PENDING");
        assertThat(state.getAttempts()).isEqualTo(1);
        assertThat(state.getNextAttemptAt()).isAfter(Instant.now());
        verifyNoInteractions(webhooks);
    }

    @Test void exactProviderTransactionAndAllocationAreVerifiedWithoutReplay() {
        makePaid(); when(provider.activePurchases(44L, "grun_pro_monthly")).thenReturn(List.of(evidence(false)));
        request(); service.verifyOne(44L); service.verifyOne(44L);
        assertThat(state.getStatus()).isEqualTo("VERIFIED");
        assertThat(state.getNextAttemptAt()).isNull();
        verify(provider, times(1)).activePurchases(44L, "grun_pro_monthly");
        verifyNoInteractions(webhooks);
    }

    @Test void missingAllocationRecoversOnlyServerFetchedEventThroughExistingProcessor() {
        when(provider.activePurchases(44L, "grun_pro_monthly")).thenReturn(List.of(evidence(false)));
        var payload = JsonNodeFactory.instance.objectNode().put("api_version", "1.0");
        when(provider.recoverablePurchaseEvent(eq(44L), any())).thenReturn(payload);
        when(webhooks.processWebhook("Bearer server-only", payload)).thenAnswer(i -> { makePaid(); return null; });
        request(); service.verifyOne(44L);
        assertThat(state.getStatus()).isEqualTo("VERIFIED");
        verify(webhooks, times(1)).processWebhook("Bearer server-only", payload);
    }

    @Test void ownershipConflictCannotReplayOrAllocate() {
        when(provider.activePurchases(44L, "grun_pro_monthly")).thenReturn(List.of(evidence(true)));
        request(); service.verifyOne(44L);
        assertThat(state.getStatus()).isEqualTo("REQUIRES_REVIEW");
        assertThat(state.getNextAttemptAt()).isNull();
        verifyNoInteractions(webhooks);
    }

    @Test void providerFailureIsNotPaymentFailureAndStopsAfterBoundedRetries() {
        when(provider.activePurchases(44L, "grun_pro_monthly")).thenThrow(new IllegalStateException("API unavailable"));
        request(); service.verifyOne(44L);
        assertThat(state.getStatus()).isEqualTo("PROVIDER_UNAVAILABLE");
        state.setAttempts(7); state.setNextAttemptAt(Instant.now().minusSeconds(1));
        service.verifyOne(44L);
        assertThat(state.getStatus()).isEqualTo("REQUIRES_REVIEW");
        assertThat(state.getNextAttemptAt()).isNull();
        verifyNoInteractions(webhooks);
    }

    @Test void activeLeasePreventsTwoWorkersAndDifferentAttemptCannotReplacePending() {
        request(); state.setLeaseUntil(Instant.now().plusSeconds(120));
        service.verifyOne(44L);
        verifyNoInteractions(provider);
        assertThatThrownBy(() -> service.request("test@example.com", "grun_pro_monthly", "attempt:another"))
                .isInstanceOf(com.grun.calorietracker.exception.RequestConflictException.class);
    }

    @Test void unsupportedProductIsRejectedBeforePersistingAnything() {
        assertThatThrownBy(() -> service.request("test@example.com", "fake_pro", "attempt:1234"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(states, provider, webhooks);
    }

    private void request() { service.request("test@example.com", "grun_pro_monthly", "attempt:1234"); }
    private RevenueCatPurchaseEvidenceClient.Evidence evidence(boolean conflict) {
        return new RevenueCatPurchaseEvidenceClient.Evidence("tx1", "grun_pro_monthly", "SANDBOX", "APP_STORE",
                purchased, Instant.now().plusSeconds(3600), conflict);
    }
    private void makePaid() {
        current.setPlanType(SubscriptionPlan.PRO); current.setProvider(PaymentProvider.REVENUECAT);
        current.setProviderProductId("grun_pro_monthly"); current.setActiveEntitlement(true);
        current.setAiCreditAllocationReference(SubscriptionAllocationIdentity.of("SANDBOX", "APP_STORE", SubscriptionPlan.PRO,
                "grun_pro_monthly", "tx1", purchased.toEpochMilli()));
    }
}
