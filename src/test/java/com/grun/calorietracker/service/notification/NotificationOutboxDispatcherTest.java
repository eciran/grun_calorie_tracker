package com.grun.calorietracker.service.notification;

import com.grun.calorietracker.config.NotificationDeliveryProperties;
import com.grun.calorietracker.config.PushProperties;
import com.grun.calorietracker.service.push.PushProviderClient;
import com.grun.calorietracker.service.AdminSubscriptionNotificationService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class NotificationOutboxDispatcherTest {
    @Test
    void independentKillSwitchPreventsClaimAndProviderIo() {
        NotificationOutboxClaimService claims = mock(NotificationOutboxClaimService.class);
        NotificationDispatchStateService state = mock(NotificationDispatchStateService.class);
        PushProviderClient provider = mock(PushProviderClient.class);
        NotificationDeliveryProperties delivery = new NotificationDeliveryProperties();
        delivery.setEnabled(false);
        PushProperties push = new PushProperties();
        push.setEnabled(true);
        NotificationOutboxDispatcher dispatcher = new NotificationOutboxDispatcher(
                claims, state, List.of(provider), delivery, new NotificationReleaseGate(delivery), push,
                mock(AdminSubscriptionNotificationService.class), Clock.systemUTC());

        assertEquals(0, dispatcher.dispatchOnce());
        verifyNoInteractions(claims, state, provider);
    }

    @Test
    void dryRunStagePreventsClaimEvenWhenDeploymentSwitchIsOn() {
        NotificationOutboxClaimService claims = mock(NotificationOutboxClaimService.class);
        NotificationDispatchStateService state = mock(NotificationDispatchStateService.class);
        PushProviderClient provider = mock(PushProviderClient.class);
        NotificationDeliveryProperties delivery = new NotificationDeliveryProperties();
        delivery.setEnabled(true);
        delivery.setStage(com.grun.calorietracker.enums.NotificationReleaseStage.DRY_RUN);
        PushProperties push = new PushProperties();
        push.setEnabled(true);
        NotificationOutboxDispatcher dispatcher = new NotificationOutboxDispatcher(
                claims, state, List.of(provider), delivery, new NotificationReleaseGate(delivery), push,
                mock(AdminSubscriptionNotificationService.class), Clock.systemUTC());

        assertEquals(0, dispatcher.dispatchOnce());
        verifyNoInteractions(claims, state, provider);
    }
}
