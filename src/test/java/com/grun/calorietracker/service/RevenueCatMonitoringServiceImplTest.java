package com.grun.calorietracker.service;

import com.grun.calorietracker.config.RevenueCatProperties;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.entity.SubscriptionVerificationEntity;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.repository.SubscriptionProviderEventRepository;
import com.grun.calorietracker.repository.SubscriptionVerificationRepository;
import com.grun.calorietracker.service.impl.RevenueCatMonitoringServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueCatMonitoringServiceImplTest {
    @Mock SubscriptionProviderEventRepository events;
    @Mock SubscriptionVerificationRepository verifications;
    @Mock RevenueCatPurchaseEvidenceClient evidenceClient;
    @Mock SubscriptionService subscriptions;
    RevenueCatProperties properties;
    RevenueCatMonitoringServiceImpl service;

    @BeforeEach
    void setup() {
        properties = new RevenueCatProperties();
        properties.getApi().setVerificationEnvironment("sandbox");
        service = new RevenueCatMonitoringServiceImpl(properties, RestClient.builder(), events,
                verifications, evidenceClient, subscriptions);
    }

    @Test
    void customerEvidenceCombinesBackendVerificationAndRedactedProviderProof() {
        var backend = new SubscriptionDto();
        backend.setPlanType(SubscriptionPlan.PRO);
        var state = new SubscriptionVerificationEntity();
        state.setUserId(44L);
        state.setStatus("VERIFIED");
        state.setProductId("grun_pro_monthly");
        state.setAttempts(2);
        state.setAllocationReference("opaque-allocation");
        state.setUpdatedAt(Instant.now());
        when(subscriptions.getUserSubscriptionForAdmin(44L)).thenReturn(backend);
        when(verifications.findById(44L)).thenReturn(Optional.of(state));
        when(evidenceClient.activePurchases(44L)).thenReturn(List.of(new RevenueCatPurchaseEvidenceClient.Evidence(
                "transaction-1", "grun_pro_monthly", "SANDBOX", "APP_STORE",
                Instant.now().minusSeconds(30), Instant.now().plusSeconds(3600), false)));

        var result = service.getCustomerEvidence(44L);

        assertThat(result.providerReachable()).isTrue();
        assertThat(result.backendSubscription().getPlanType()).isEqualTo(SubscriptionPlan.PRO);
        assertThat(result.verification().allocationReferencePresent()).isTrue();
        assertThat(result.purchases()).singleElement().satisfies(item -> {
            assertThat(item.transactionId()).isEqualTo("transaction-1");
            assertThat(item.ownershipConflict()).isFalse();
        });
    }

    @Test
    void providerFailureIsVisibleWithoutHidingBackendState() {
        var backend = new SubscriptionDto();
        backend.setPlanType(SubscriptionPlan.FREE);
        when(subscriptions.getUserSubscriptionForAdmin(44L)).thenReturn(backend);
        when(verifications.findById(44L)).thenReturn(Optional.empty());
        when(evidenceClient.activePurchases(44L)).thenThrow(new IllegalStateException("provider unavailable"));

        var result = service.getCustomerEvidence(44L);

        assertThat(result.providerReachable()).isFalse();
        assertThat(result.purchases()).isEmpty();
        assertThat(result.backendSubscription().getPlanType()).isEqualTo(SubscriptionPlan.FREE);
        assertThat(result.statusMessage()).doesNotContain("secret");
    }
}
