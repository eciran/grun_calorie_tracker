package com.grun.calorietracker.service;

import com.grun.calorietracker.config.RevenueCatProperties;
import com.grun.calorietracker.dto.AiQuotaPromotionDecisionRequestDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.entity.AiQuotaPromotionPolicyEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.repository.AiQuotaPromotionPolicyRepository;
import com.grun.calorietracker.repository.AiQuotaPromotionReservationRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.AiQuotaPromotionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiQuotaPromotionServiceImplTest {
    @Mock AiQuotaPromotionPolicyRepository policies;
    @Mock AiQuotaPromotionReservationRepository reservations;
    @Mock UserRepository users;
    @Mock SubscriptionService subscriptions;
    @Mock AdminAuditService auditService;

    private AiQuotaPromotionServiceImpl service;
    private AiQuotaPromotionPolicyEntity policy;
    private SubscriptionDto subscription;

    @BeforeEach
    void setUp() {
        UserEntity user = new UserEntity();
        user.setId(42L);
        user.setEmail("paid@example.com");
        policy = new AiQuotaPromotionPolicyEntity();
        policy.setId(1L);
        policy.setVersion(0L);
        policy.setEnabled(true);
        policy.setMinimumIntervalHours(12);
        policy.setMaxImpressions24h(2);
        policy.setDismissCooldownHours(12);
        policy.setRolloutPercentage(100);
        policy.setCampaignVersion(1L);
        policy.setChangeReason("test");
        policy.setUpdatedBy("test");
        policy.setUpdatedAt(Instant.parse("2026-11-03T10:00:00Z"));

        RevenueCatProperties properties = new RevenueCatProperties();
        properties.getProducts().setAiAddonQuotas(Map.of(
                "grun_ai_15_credits", 15,
                "grun_ai_50_credits", 50));
        properties.getProducts().setAiAddonValidityDays(Map.of(
                "grun_ai_15_credits", 30,
                "grun_ai_50_credits", 30));

        service = new AiQuotaPromotionServiceImpl(policies, reservations, users, subscriptions,
                properties, auditService, Clock.fixed(policy.getUpdatedAt(), ZoneOffset.UTC));
        subscription = subscription(SubscriptionPlan.PLUS, 0, true);

        lenient().when(users.findByEmail("paid@example.com")).thenReturn(Optional.of(user));
        lenient().when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
        lenient().when(policies.findById(1L)).thenReturn(Optional.of(policy));
        lenient().when(subscriptions.getCurrentSubscription("paid@example.com")).thenReturn(subscription);
        lenient().when(reservations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void exhaustedPlusUserReceivesCreditOffersAndProUpgrade() {
        var decision = service.decide("paid@example.com", request("paid-session-01", false));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.currentPlan()).isEqualTo(SubscriptionPlan.PLUS);
        assertThat(decision.upgradePlan()).isEqualTo(SubscriptionPlan.PRO);
        assertThat(decision.remainingCredits()).isZero();
        assertThat(decision.creditOffers()).extracting("credits").containsExactly(15, 50);
        assertThat(decision.reservationToken()).hasSize(64);
    }

    @Test
    void proUserReceivesCreditOffersWithoutUpgrade() {
        when(subscriptions.getCurrentSubscription("paid@example.com"))
                .thenReturn(subscription(SubscriptionPlan.PRO, 0, true));

        var decision = service.decide("paid@example.com", request("paid-session-02", false));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.upgradePlan()).isNull();
    }

    @Test
    void freeInactiveAndNonExhaustedAccountsAreRejected() {
        when(subscriptions.getCurrentSubscription("paid@example.com"))
                .thenReturn(subscription(SubscriptionPlan.FREE, 0, true));
        assertThat(service.decide("paid@example.com", request("paid-session-03", false)).reason())
                .isEqualTo("PAID_PLAN_REQUIRED");

        when(subscriptions.getCurrentSubscription("paid@example.com"))
                .thenReturn(subscription(SubscriptionPlan.PLUS, 0, false));
        assertThat(service.decide("paid@example.com", request("paid-session-04", false)).reason())
                .isEqualTo("ACTIVE_ENTITLEMENT_REQUIRED");

        when(subscriptions.getCurrentSubscription("paid@example.com"))
                .thenReturn(subscription(SubscriptionPlan.PLUS, 1, true));
        assertThat(service.decide("paid@example.com", request("paid-session-05", false)).reason())
                .isEqualTo("QUOTA_NOT_EXHAUSTED");

        when(subscriptions.getCurrentSubscription("paid@example.com"))
                .thenReturn(subscription(SubscriptionPlan.PLUS, -1, true));
        assertThat(service.decide("paid@example.com", request("paid-session-09", false)).reason())
                .isEqualTo("QUOTA_NOT_EXHAUSTED");
    }

    @Test
    void pendingPurchaseAndTwelveHourLimitsSuppressOffers() {
        assertThat(service.decide("paid@example.com", request("paid-session-06", true)).reason())
                .isEqualTo("PURCHASE_PENDING");

        when(reservations.existsByUserAndImpressionAtAfter(any(), any())).thenReturn(true);
        assertThat(service.decide("paid@example.com", request("paid-session-07", false)).reason())
                .isEqualTo("MINIMUM_INTERVAL");

        when(reservations.existsByUserAndImpressionAtAfter(any(), any())).thenReturn(false);
        when(reservations.existsByUserAndDismissedAtAfter(any(), any())).thenReturn(true);
        assertThat(service.decide("paid@example.com", request("paid-session-08", false)).reason())
                .isEqualTo("DISMISS_COOLDOWN");
    }

    private AiQuotaPromotionDecisionRequestDto request(String sessionId, boolean pendingPurchase) {
        return new AiQuotaPromotionDecisionRequestDto(sessionId, pendingPurchase);
    }

    private SubscriptionDto subscription(SubscriptionPlan plan, int remaining, boolean active) {
        SubscriptionDto dto = new SubscriptionDto();
        dto.setPlanType(plan);
        dto.setAiRemainingThisPeriod(remaining);
        dto.setActiveEntitlement(active);
        return dto;
    }
}
