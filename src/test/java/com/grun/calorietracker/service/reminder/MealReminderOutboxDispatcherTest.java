package com.grun.calorietracker.service.reminder;

import com.grun.calorietracker.config.MealReminderDeliveryProperties;
import com.grun.calorietracker.config.PushProperties;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.UserPushTokenEntity;
import com.grun.calorietracker.enums.PushProvider;
import com.grun.calorietracker.service.push.PushProviderClient;
import com.grun.calorietracker.service.push.PushProviderSendResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MealReminderOutboxDispatcherTest {
    private final Instant now = Instant.parse("2026-08-29T12:00:00Z");

    @Test
    void killSwitchPreventsClaimingAndProviderCalls() {
        MealReminderClaimService claims = mock(MealReminderClaimService.class);
        MealReminderDispatchStateService state = mock(MealReminderDispatchStateService.class);
        PushProviderClient provider = mock(PushProviderClient.class);
        MealReminderDeliveryProperties delivery = enabledDelivery();
        delivery.setDeliveryEnabled(false);

        int count = dispatcher(claims, state, provider, delivery).dispatchOnce();

        assertEquals(0, count);
        verify(claims, never()).claimOutbox(anyString(), org.mockito.ArgumentMatchers.any());
        verify(provider, never()).send(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void passesRemainingSlotTtlAndPersistsProviderOutcome() {
        MealReminderClaimService claims = mock(MealReminderClaimService.class);
        MealReminderDispatchStateService state = mock(MealReminderDispatchStateService.class);
        PushProviderClient provider = mock(PushProviderClient.class);
        when(provider.provider()).thenReturn(PushProvider.EXPO);
        when(claims.claimOutbox(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(now)))
                .thenReturn(List.of(9L));
        when(state.revalidateAndPrepare(org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(now)))
                .thenReturn(new MealReminderDispatchStateService.PreparedOutbox(
                        9L, List.of(17L), now.plusSeconds(90)));
        UserPushTokenEntity token = new UserPushTokenEntity();
        NotificationEntity notification = new NotificationEntity();
        when(state.payload(17L)).thenReturn(new MealReminderDispatchStateService.AttemptPayload(
                17L, token, notification, now.plusSeconds(90)));
        PushProviderSendResult accepted = PushProviderSendResult.sent("ticket-1");
        when(provider.send(token, notification, Duration.ofSeconds(90))).thenReturn(accepted);

        int count = dispatcher(claims, state, provider, enabledDelivery()).dispatchOnce();

        assertEquals(1, count);
        verify(provider).send(token, notification, Duration.ofSeconds(90));
        verify(state).recordProviderResult(17L, accepted, now);
    }

    @Test
    void providerTimeoutBecomesUncertainAndIsNeverBlindRetried() {
        MealReminderClaimService claims = mock(MealReminderClaimService.class);
        MealReminderDispatchStateService state = mock(MealReminderDispatchStateService.class);
        PushProviderClient provider = mock(PushProviderClient.class);
        when(provider.provider()).thenReturn(PushProvider.EXPO);
        when(claims.claimOutbox(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(now)))
                .thenReturn(List.of(9L));
        when(state.revalidateAndPrepare(org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(now)))
                .thenReturn(new MealReminderDispatchStateService.PreparedOutbox(9L, List.of(17L), now.plusSeconds(90)));
        UserPushTokenEntity token = new UserPushTokenEntity();
        NotificationEntity notification = new NotificationEntity();
        when(state.payload(17L)).thenReturn(new MealReminderDispatchStateService.AttemptPayload(
                17L, token, notification, now.plusSeconds(90)));
        when(provider.send(token, notification, Duration.ofSeconds(90)))
                .thenThrow(new IllegalStateException("provider timeout after write"));

        assertEquals(1, dispatcher(claims, state, provider, enabledDelivery()).dispatchOnce());

        verify(provider).send(token, notification, Duration.ofSeconds(90));
        verify(state).recordProviderResult(org.mockito.ArgumentMatchers.eq(17L),
                org.mockito.ArgumentMatchers.argThat(result -> result.uncertain()
                        && !result.sent() && "provider timeout after write".equals(result.errorMessage())),
                org.mockito.ArgumentMatchers.eq(now));
    }

    @Test
    void brokenOutboxDoesNotRollbackOrStopTheRemainingClaimedPage() {
        MealReminderClaimService claims = mock(MealReminderClaimService.class);
        MealReminderDispatchStateService state = mock(MealReminderDispatchStateService.class);
        PushProviderClient provider = mock(PushProviderClient.class);
        when(provider.provider()).thenReturn(PushProvider.EXPO);
        when(claims.claimOutbox(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(now)))
                .thenReturn(List.of(9L, 10L));
        doThrow(new IllegalStateException("row unavailable")).when(state).revalidateAndPrepare(
                org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(now));
        when(state.revalidateAndPrepare(org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(now)))
                .thenReturn(new MealReminderDispatchStateService.PreparedOutbox(10L, List.of(18L), now.plusSeconds(90)));
        UserPushTokenEntity token = new UserPushTokenEntity();
        NotificationEntity notification = new NotificationEntity();
        when(state.payload(18L)).thenReturn(new MealReminderDispatchStateService.AttemptPayload(
                18L, token, notification, now.plusSeconds(90)));
        PushProviderSendResult accepted = PushProviderSendResult.sent("ticket-2");
        when(provider.send(token, notification, Duration.ofSeconds(90))).thenReturn(accepted);

        assertEquals(1, dispatcher(claims, state, provider, enabledDelivery()).dispatchOnce());

        verify(state).recordProviderResult(18L, accepted, now);
    }

    private MealReminderOutboxDispatcher dispatcher(
            MealReminderClaimService claims, MealReminderDispatchStateService state,
            PushProviderClient provider, MealReminderDeliveryProperties delivery) {
        PushProperties push = new PushProperties();
        push.setEnabled(true);
        push.setProvider(PushProvider.EXPO);
        MealReminderPolicyFactory policyFactory = mock(MealReminderPolicyFactory.class);
        when(policyFactory.current()).thenReturn(new MealReminderPolicy(
                "test", MealReminderContract.Mode.PILOT, true, true, MealReminderContract.DEFAULT_TIMES,
                MealReminderContract.MAX_SLOT_AGE, 3, 3, 1, MealReminderContract.MIN_MEAL_REMINDER_GAP,
                MealReminderContract.ROUTINE_REMINDER_GAP, MealReminderContract.DEFAULT_QUIET_START,
                MealReminderContract.DEFAULT_QUIET_END));
        return new MealReminderOutboxDispatcher(claims, state, List.of(provider), delivery, push,
                Clock.fixed(now, ZoneOffset.UTC), policyFactory);
    }

    private MealReminderDeliveryProperties enabledDelivery() {
        MealReminderDeliveryProperties delivery = new MealReminderDeliveryProperties();
        delivery.setDeliveryEnabled(true);
        delivery.setMode(MealReminderContract.Mode.PILOT);
        return delivery;
    }
}
