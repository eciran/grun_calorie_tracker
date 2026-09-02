package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.impl.FreePromotionServiceImpl;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FreePromotionServiceImplTest {
    @Mock FreePromotionPolicyRepository policies;
    @Mock FreePromotionUserStateRepository userStates;
    @Mock FreePromotionReservationRepository reservations;
    @Mock UserRepository users;
    @Mock SubscriptionRepository subscriptions;
    @Mock FoodLogsRepository foodLogs;
    @Mock AdminAuditService auditService;

    private final Instant now = Instant.parse("2026-08-29T10:00:00Z");
    private FreePromotionServiceImpl service;
    private UserEntity user;
    private FreePromotionPolicyEntity policy;

    @BeforeEach
    void setUp() {
        service = new FreePromotionServiceImpl(policies, userStates, reservations, users, subscriptions, foodLogs,
                auditService, Clock.fixed(now, ZoneOffset.UTC));
        user = new UserEntity(); user.setId(42L); user.setEmail("free@example.com");
        policy = policy(false);
        lenient().when(users.findByEmail("free@example.com")).thenReturn(Optional.of(user));
        lenient().when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
        lenient().when(policies.findById(1L)).thenReturn(Optional.of(policy));
        lenient().when(subscriptions.findByUser(user)).thenReturn(Optional.empty());
        lenient().when(reservations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void disabledPolicyFailsClosedButCountsDistinctColdLaunchSession() {
        when(userStates.findByUser(user)).thenReturn(Optional.empty());

        FreePromotionDecisionDto result = service.decide("free@example.com", decision("session-one", false));

        assertThat(result.eligible()).isFalse();
        assertThat(result.reason()).isEqualTo("POLICY_DISABLED");
        verify(userStates).save(argThat(state -> state.getSessionNumber() == 1
                && state.getLastSessionId().equals("session-one")));
        verifyNoInteractions(foodLogs);
        verify(reservations, never()).save(any());
    }

    @Test
    void eligibleFreeUserReceivesShortLivedServerReservationAfterSecondSession() {
        policy.setEnabled(true);
        FreePromotionUserStateEntity state = state(1, "older-session");
        when(userStates.findByUser(user)).thenReturn(Optional.of(state));
        when(foodLogs.countByUser(user)).thenReturn(1L);
        when(reservations.countByUserAndImpressionAtAfter(eq(user), any())).thenReturn(0L);

        FreePromotionDecisionDto result = service.decide("free@example.com", decision("session-two", false));

        assertThat(result.eligible()).isTrue();
        assertThat(result.reservationToken()).hasSize(64);
        assertThat(result.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(5)));
        verify(reservations).save(argThat(reservation -> reservation.getCampaignVersion() == 1L
                && reservation.getPlacement() == FreePromotionPlacement.HOME_RETURN));
    }

    @Test
    void paidAndPendingPurchaseAccountsAreSuppressedBeforeActivityChecks() {
        policy.setEnabled(true);
        when(userStates.findByUser(user)).thenReturn(Optional.of(state(2, "old")));
        assertThat(service.decide("free@example.com", decision("pending", true)).reason()).isEqualTo("PURCHASE_PENDING");

        SubscriptionEntity paid = new SubscriptionEntity(); paid.setPlanType(SubscriptionPlan.PRO);
        when(subscriptions.findByUser(user)).thenReturn(Optional.of(paid));
        assertThat(service.decide("free@example.com", decision("paid", false)).reason()).isEqualTo("NOT_VERIFIED_FREE");
        verifyNoInteractions(foodLogs);
    }

    @Test
    void sessionIntervalDismissAndRollingLimitsAreEvaluatedInOrder() {
        policy.setEnabled(true);
        when(userStates.findByUser(user)).thenReturn(Optional.of(state(2, "old")));
        when(foodLogs.countByUser(user)).thenReturn(1L);

        when(reservations.existsByUserAndSessionIdAndImpressionAtIsNotNull(user, "same-session")).thenReturn(true);
        assertThat(service.decide("free@example.com", decision("same-session", false)).reason()).isEqualTo("SESSION_LIMIT_REACHED");

        when(reservations.existsByUserAndImpressionAtAfter(eq(user), any())).thenReturn(true);
        assertThat(service.decide("free@example.com", decision("interval-session", false)).reason()).isEqualTo("MINIMUM_INTERVAL");

        when(reservations.existsByUserAndImpressionAtAfter(eq(user), any())).thenReturn(false);
        when(reservations.existsByUserAndDismissedAtAfter(eq(user), any())).thenReturn(true);
        assertThat(service.decide("free@example.com", decision("dismiss-session", false)).reason()).isEqualTo("DISMISS_COOLDOWN");

        when(reservations.existsByUserAndDismissedAtAfter(eq(user), any())).thenReturn(false);
        when(reservations.countByUserAndImpressionAtAfter(eq(user), any())).thenReturn(2L);
        assertThat(service.decide("free@example.com", decision("rolling-session", false)).reason()).isEqualTo("ROLLING_LIMIT");
    }

    @Test
    void impressionRechecksKillSwitchAndIsIdempotent() {
        FreePromotionReservationEntity reservation = reservation();
        when(reservations.findByReservationTokenAndUser(reservation.getReservationToken(), user)).thenReturn(Optional.of(reservation));
        policy.setEnabled(true);

        service.recordImpression("free@example.com", new FreePromotionEventRequestDto(reservation.getReservationToken()));
        service.recordImpression("free@example.com", new FreePromotionEventRequestDto(reservation.getReservationToken()));

        assertThat(reservation.getImpressionAt()).isEqualTo(now);
        verify(reservations, times(1)).save(reservation);

        FreePromotionReservationEntity disabledReservation = reservation();
        disabledReservation.setReservationToken("b".repeat(64));
        when(reservations.findByReservationTokenAndUser(disabledReservation.getReservationToken(), user)).thenReturn(Optional.of(disabledReservation));
        policy.setEnabled(false);
        assertThatThrownBy(() -> service.recordImpression("free@example.com",
                new FreePromotionEventRequestDto(disabledReservation.getReservationToken())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("disabled");
    }

    @Test
    void adminUpdateUsesOptimisticVersionAndCreatesAuditRecord() {
        when(policies.findByIdForUpdate(1L)).thenReturn(Optional.of(policy));
        when(policies.save(policy)).thenReturn(policy);
        AdminFreePromotionPolicyRequestDto request = new AdminFreePromotionPolicyRequestDto(0L, true,
                12, 2, 12, 2, 10, "Start a small internal pilot");

        AdminFreePromotionPolicyDto updated = service.updatePolicy(request, "growth-admin", "cid-1");

        assertThat(updated.enabled()).isTrue();
        assertThat(updated.rolloutPercentage()).isEqualTo(10);
        assertThat(updated.campaignVersion()).isEqualTo(2L);
        verify(auditService).record(eq("growth-admin"), eq(AdminAuditActionType.FREE_PROMOTION_POLICY_UPDATE),
                eq(AdminAuditTargetType.FREE_PROMOTION_POLICY), eq("1"), anyMap(), anyMap(), eq("cid-1"));

        policy.setVersion(3L);
        assertThatThrownBy(() -> service.updatePolicy(request, "growth-admin", "cid-2"))
                .isInstanceOf(OptimisticLockException.class);
    }

    private FreePromotionDecisionRequestDto decision(String sessionId, boolean pendingPurchase) {
        return new FreePromotionDecisionRequestDto(sessionId, FreePromotionPlacement.HOME_RETURN, pendingPurchase);
    }
    private FreePromotionPolicyEntity policy(boolean enabled) {
        FreePromotionPolicyEntity entity = new FreePromotionPolicyEntity();
        entity.setId(1L); entity.setVersion(0L); entity.setEnabled(enabled); entity.setMinimumIntervalHours(12);
        entity.setMaxImpressions24h(2); entity.setDismissCooldownHours(12); entity.setMinimumSessionNumber(2);
        entity.setRolloutPercentage(100); entity.setCampaignVersion(1L); entity.setChangeReason("Safe default");
        entity.setUpdatedBy("migration"); entity.setUpdatedAt(now); return entity;
    }
    private FreePromotionUserStateEntity state(int number, String sessionId) {
        FreePromotionUserStateEntity state = new FreePromotionUserStateEntity();
        state.setUser(user); state.setSessionNumber(number); state.setLastSessionId(sessionId); state.setUpdatedAt(now.minusSeconds(60));
        return state;
    }
    private FreePromotionReservationEntity reservation() {
        FreePromotionReservationEntity reservation = new FreePromotionReservationEntity();
        reservation.setUser(user); reservation.setReservationToken("a".repeat(64)); reservation.setSessionId("session-two");
        reservation.setPlacement(FreePromotionPlacement.HOME_RETURN); reservation.setCampaignVersion(1L);
        reservation.setReservedAt(now.minusSeconds(30)); reservation.setExpiresAt(now.plusSeconds(270)); return reservation;
    }
}
