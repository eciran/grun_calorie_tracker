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
    final com.grun.calorietracker.repository.SubscriptionProviderEventRepository events = mock(com.grun.calorietracker.repository.SubscriptionProviderEventRepository.class);
    final RevenueCatProperties properties = new RevenueCatProperties();
    SubscriptionPurchaseVerificationService service;
    SubscriptionVerificationEntity state;
    SubscriptionDto current;
    final Instant purchased = Instant.now().minusSeconds(30).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

    @BeforeEach void setup() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        properties.getProducts().setPro(List.of("grun_pro_monthly"));
        properties.getProducts().setPlus(List.of("grun_plus_monthly"));
        properties.getProducts().getAiAddonQuotas().put("grun_ai_15_credits", 15);
        properties.getApi().setEnabled(true);
        properties.getApi().setSecretKey("fixture-secret");
        properties.getApi().setProjectId("fixture-project");
        properties.setWebhookAuthorization("Bearer server-only");
        service = new SubscriptionPurchaseVerificationService(users, states, provider, properties, subscriptions, webhooks, manager, events);
        var user = new UserEntity(); user.setId(44L); user.setEmail("test@example.com");
        when(users.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));
        when(users.findByEmail("test@example.com")).thenReturn(Optional.of(user));
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

    @Test void preparationReservesOneAttemptBeforeAnyStorePayment() {
        var result = service.prepare("test@example.com", "grun_pro_monthly", "prepare:1234");
        assertThat(result.status()).isEqualTo(SubscriptionPurchaseVerificationService.Status.PREPARED);
        assertThat(state.getNextAttemptAt()).isNull();
        assertThatThrownBy(() -> service.prepare("test@example.com", "grun_plus_monthly", "prepare:5678"))
                .isInstanceOf(com.grun.calorietracker.exception.RequestConflictException.class);
        assertThat(service.pending("test@example.com").attemptId()).isEqualTo("prepare:1234");
        service.request("test@example.com", "grun_pro_monthly", "prepare:1234");
        assertThat(state.getNextAttemptAt()).isNotNull();
        verifyNoInteractions(provider, webhooks);
    }

    @Test void preparationFailsClosedWhenProviderIsNotConfigured() {
        properties.getApi().setEnabled(false);
        assertThatThrownBy(() -> service.prepare("test@example.com", "grun_pro_monthly", "prepare:1234"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(state).isNull();
    }

    @Test void addonRequiresActivePaidSubscriptionBeforeCheckout() {
        assertThatThrownBy(() -> service.prepare("test@example.com", "grun_ai_15_credits", "prepare:1234"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(state).isNull();
    }

    @Test void addonVerificationRequiresExactProcessedTransactionNotBalanceIncrease() {
        makePaid();
        current.setAiAddonQuota(500);
        service.prepare("test@example.com", "grun_ai_15_credits", "prepare:1234");
        service.request("test@example.com", "grun_ai_15_credits", "prepare:1234", "addon-tx");
        var payload = JsonNodeFactory.instance.objectNode();
        payload.putObject("event").put("transaction_id", "addon-tx").put("environment", "PRODUCTION");
        when(provider.recoverableAddonPurchase(eq(44L), eq("grun_ai_15_credits"), any(), eq("addon-tx"))).thenReturn(payload);
        service.verifyOne(44L);
        assertThat(state.getStatus()).isEqualTo("PENDING");
        when(events.existsByUser_IdAndProviderAndProductIdAndTransactionIdAndEnvironmentAndEventTypeAndStatus(
                44L, PaymentProvider.REVENUECAT, "grun_ai_15_credits", "addon-tx", "PRODUCTION", "NON_RENEWING_PURCHASE",
                com.grun.calorietracker.enums.SubscriptionProviderEventStatus.PROCESSED)).thenReturn(true);
        current.setAiAddonQuota(0);
        state.setNextAttemptAt(Instant.now().minusSeconds(1));
        service.verifyOne(44L);
        assertThat(state.getStatus()).isEqualTo("VERIFIED");
        assertThat(state.getProviderTransactionId()).isEqualTo("addon-tx");
        assertThat(state.getAllocationReference()).isNull();
    }

    @Test void transactionIdentityCannotBeChangedOnTheSameAttempt() {
        request();
        service.request("test@example.com", "grun_pro_monthly", state.getAttemptId(), "tx-first");
        assertThatThrownBy(() -> service.request("test@example.com", "grun_pro_monthly", state.getAttemptId(), "tx-other"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void scheduledChangeIsRequeuedAfterItsEffectiveDate() {
        makePaid();
        request();
        state.setStatus("SCHEDULED");
        state.setNextAttemptAt(null);
        state.setCreatedAt(Instant.now().minusSeconds(40L * 86400));
        var result = request();
        assertThat(result.status()).isEqualTo(SubscriptionPurchaseVerificationService.Status.PENDING);
        assertThat(state.getNextAttemptAt()).isNotNull();
        when(provider.activePurchases(44L, "grun_pro_monthly")).thenReturn(List.of(evidence(false)));
        service.verifyOne(44L);
        assertThat(state.getStatus()).isEqualTo("VERIFIED");
    }

    @Test void scheduledWorkerGetsFreshRecoveryWindowWhenItBecomesDue() {
        makePaid(); request();
        state.setStatus("SCHEDULED");
        state.setCreatedAt(Instant.now().minusSeconds(40L * 86400));
        state.setRecoveryStartedAt(state.getCreatedAt());
        state.setAttempts(32);
        when(provider.activePurchases(44L, "grun_pro_monthly")).thenReturn(List.of(evidence(false)));
        service.verifyOne(44L);
        assertThat(state.getStatus()).isEqualTo("VERIFIED");
    }

    @Test void offerCodeCannotConfirmAnUnrelatedExistingSubscription() {
        makePaid();
        service.prepare("test@example.com", SubscriptionPurchaseVerificationService.STORE_OFFER_PRODUCT, "prepare:1234");
        service.request("test@example.com", SubscriptionPurchaseVerificationService.STORE_OFFER_PRODUCT, "prepare:1234");
        when(provider.activePurchases(44L)).thenReturn(List.of(evidence(false)));
        service.verifyOne(44L);
        assertThat(state.getStatus()).isEqualTo("PENDING");
        assertThat(state.getReasonCode()).isEqualTo("AWAITING_OFFER_TRANSACTION");
        verifyNoInteractions(webhooks);
    }

    @Test void explicitRecheckRestartsExpiredRecoveryWindow() {
        request();
        state.setStatus("REQUIRES_REVIEW");
        state.setNextAttemptAt(null);
        state.setAttempts(32);
        state.setCreatedAt(Instant.now().minusSeconds(90000));
        state.setUpdatedAt(Instant.now().minusSeconds(120));
        request();
        service.verifyOne(44L);
        assertThat(state.getStatus()).isEqualTo("PENDING");
        assertThat(state.getAttempts()).isEqualTo(1);
        assertThat(state.getRecoveryStartedAt()).isAfter(Instant.now().minusSeconds(60));
        verify(provider).activePurchases(44L, "grun_pro_monthly");
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
        when(provider.recoverablePurchaseEvents(eq(44L), any())).thenReturn(List.of(payload));
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

    @Test void providerFailureContinuesSlowRecoveryAfterEightAttempts() {
        when(provider.activePurchases(44L, "grun_pro_monthly")).thenThrow(new IllegalStateException("API unavailable"));
        request(); service.verifyOne(44L);
        assertThat(state.getStatus()).isEqualTo("PROVIDER_UNAVAILABLE");
        state.setAttempts(7); state.setNextAttemptAt(Instant.now().minusSeconds(1));
        service.verifyOne(44L);
        assertThat(state.getStatus()).isEqualTo("REQUIRES_REVIEW");
        assertThat(state.getNextAttemptAt()).isAfter(Instant.now().plusSeconds(890));
        assertThat(state.getReasonCode()).isEqualTo("VERIFICATION_PROCESSING_ERROR");
        verifyNoInteractions(webhooks);
    }

    @Test void interruptedPlusSelectionReconcilesOnlyVerifiedActivePro() {
        properties.getProducts().setPlus(List.of("grun_plus_monthly"));
        makePaid();
        when(provider.activePurchases(44L)).thenReturn(List.of(evidence(false)));
        when(provider.recoverablePurchaseEvents(eq(44L), any()))
                .thenReturn(List.of(JsonNodeFactory.instance.objectNode().put("api_version", "1.0")));
        service.request("test@example.com", "grun_plus_monthly", "attempt:plus");
        service.verifyOne(44L);
        assertThat(state.getStatus()).isEqualTo("RECONCILED");
        assertThat(state.getResolvedProductId()).isEqualTo("grun_pro_monthly");
        assertThat(state.getProductId()).isEqualTo("grun_plus_monthly");
        assertThat(state.getProviderTransactionId()).isEqualTo("tx1");
        assertThat(service.request("test@example.com", "grun_plus_monthly", "attempt:plus").status())
                .isEqualTo(SubscriptionPurchaseVerificationService.Status.RECONCILED);
        verify(webhooks).processWebhook(eq("Bearer server-only"), any());
    }

    @Test void activeProWithVerifiedScheduledPlusIsNotReportedAsCompletedPlusPurchase() {
        properties.getProducts().setPlus(List.of("grun_plus_monthly"));
        makePaid();
        when(provider.activePurchases(44L)).thenReturn(List.of(evidence(false)));
        var payload = JsonNodeFactory.instance.objectNode().put("type", "PRODUCT_CHANGE");
        when(provider.recoverablePurchaseEvents(eq(44L), any())).thenReturn(List.of(payload));
        when(webhooks.processWebhook(eq("Bearer server-only"), any())).thenAnswer(i -> {
            current.setScheduledProductId("grun_plus_monthly");
            current.setScheduledChangeAt(Instant.now().plusSeconds(3600));
            return null;
        });
        service.request("test@example.com", "grun_plus_monthly", "attempt:plus");
        service.verifyOne(44L);
        assertThat(state.getStatus()).isEqualTo("SCHEDULED");
        assertThat(current.getPlanType()).isEqualTo(SubscriptionPlan.PRO);
    }

    @Test void alternativeProductOwnershipConflictNeverReconciles() {
        when(provider.activePurchases(44L)).thenReturn(List.of(evidence(true)));
        request(); service.verifyOne(44L);
        assertThat(state.getStatus()).isEqualTo("REQUIRES_REVIEW");
        assertThat(state.getReasonCode()).isEqualTo("OWNERSHIP_CONFLICT");
        assertThat(state.getNextAttemptAt()).isNull();
        verifyNoInteractions(webhooks);
    }

    @Test void recoveryWindowEventuallyStopsWithoutDroppingPurchaseRecord() {
        request();
        state.setCreatedAt(Instant.now().minusSeconds(86401));
        state.setRecoveryStartedAt(Instant.now().minusSeconds(86401));
        service.verifyOne(44L);
        assertThat(state.getStatus()).isEqualTo("REQUIRES_REVIEW");
        assertThat(state.getReasonCode()).isEqualTo("RETRY_WINDOW_EXHAUSTED");
        assertThat(state.getNextAttemptAt()).isNull();
        verifyNoInteractions(provider, webhooks);
    }

    @Test void changedRenewalAllocationReopensPreviouslyVerifiedAttempt() {
        makePaid(); when(provider.activePurchases(44L, "grun_pro_monthly")).thenReturn(List.of(evidence(false)));
        request(); service.verifyOne(44L);
        current.setAiCreditAllocationReference("b".repeat(64));
        assertThat(service.request("test@example.com", "grun_pro_monthly", "attempt:1234").status())
                .isEqualTo(SubscriptionPurchaseVerificationService.Status.PENDING);
        assertThat(state.getNextAttemptAt()).isNotNull();
    }

    @Test void activeLeasePreventsTwoWorkersAndDifferentAttemptCannotReplacePending() {
        request(); state.setLeaseUntil(Instant.now().plusSeconds(120));
        service.verifyOne(44L);
        verifyNoInteractions(provider);
        assertThatThrownBy(() -> service.request("test@example.com", "grun_pro_monthly", "attempt:another"))
                .isInstanceOf(com.grun.calorietracker.exception.RequestConflictException.class);
    }

    @Test void receiptArrivingDuringLookupRequiresFreshVerificationClaim() {
        makePaid();
        when(provider.activePurchases(44L, "grun_pro_monthly")).thenAnswer(call -> {
            state.setExpectedTransactionId("receipt-arrived");
            return List.of(evidence(false));
        });
        request(); service.verifyOne(44L);
        assertThat(state.getStatus()).isEqualTo("PENDING");
        assertThat(state.getLeaseUntil()).isNull();
        assertThat(state.getNextAttemptAt()).isNotNull();
        assertThat(state.getProviderTransactionId()).isNull();
    }

    @Test void canceledNativeCheckoutReleasesAttemptAndInvalidatesOldWorkerLease() {
        properties.getProducts().setPlus(List.of("grun_plus_monthly"));
        service.request("test@example.com", "grun_plus_monthly", "attempt:plus");
        state.setLeaseUntil(Instant.now().plusSeconds(120));
        service.cancelUnconfirmedAttempt("test@example.com", "attempt:plus");
        assertThat(state.getStatus()).isEqualTo("CANCELED");
        assertThat(state.getLeaseUntil()).isNull();
        request();
        assertThat(state.getProductId()).isEqualTo("grun_pro_monthly");
        assertThat(state.getStatus()).isEqualTo("PENDING");
        verifyNoInteractions(provider, webhooks);
    }

    @Test void cancelCannotReleaseAttemptWithPaymentEvidenceOrAnotherAttemptId() {
        request();
        service.cancelUnconfirmedAttempt("test@example.com", "attempt:other");
        assertThat(state.getStatus()).isEqualTo("PENDING");
        state.setExpectedTransactionId("client-receipt");
        service.cancelUnconfirmedAttempt("test@example.com", "attempt:1234");
        assertThat(state.getStatus()).isEqualTo("PENDING");
        state.setProviderTransactionId("tx1");
        service.cancelUnconfirmedAttempt("test@example.com", "attempt:1234");
        assertThat(state.getStatus()).isEqualTo("PENDING");
        assertThat(state.getNextAttemptAt()).isNotNull();
        verifyNoInteractions(provider, webhooks);
    }

    @Test void unsupportedProductIsRejectedBeforePersistingAnything() {
        assertThatThrownBy(() -> service.request("test@example.com", "fake_pro", "attempt:1234"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(states, provider, webhooks);
    }

    private SubscriptionPurchaseVerificationService.Result request() { return service.request("test@example.com", "grun_pro_monthly", "attempt:1234"); }
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
