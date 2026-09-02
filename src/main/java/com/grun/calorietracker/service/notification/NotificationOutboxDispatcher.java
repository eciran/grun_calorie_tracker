package com.grun.calorietracker.service.notification;

import com.grun.calorietracker.config.NotificationDeliveryProperties;
import com.grun.calorietracker.config.PushProperties;
import com.grun.calorietracker.service.push.PushProviderClient;
import com.grun.calorietracker.service.push.PushProviderSendResult;
import com.grun.calorietracker.service.AdminSubscriptionNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxDispatcher {
    private final NotificationOutboxClaimService claimService;
    private final NotificationDispatchStateService stateService;
    private final List<PushProviderClient> providerClients;
    private final NotificationDeliveryProperties properties;
    private final NotificationReleaseGate releaseGate;
    private final PushProperties pushProperties;
    private final AdminSubscriptionNotificationService subscriptionNotificationService;
    private final Clock analyticsClock;

    @Scheduled(fixedDelayString = "${grun.notifications.delivery.scan-ms:5000}")
    public void dispatchScheduled() {
        dispatchOnce();
    }

    public int dispatchOnce() {
        if (!deliveryAllowed()) return 0;
        String workerId = "notification-outbox-" + UUID.randomUUID();
        int attempted = 0;
        for (Long outboxId : claimService.claim(workerId, analyticsClock.instant())) {
            try {
                var prepared = stateService.prepare(outboxId, workerId, analyticsClock.instant());
                if (prepared == null) continue;
                for (Long attemptId : prepared.attemptIds()) {
                    if (!deliveryAllowed()) {
                        stateService.cancelForKillSwitch(outboxId, analyticsClock.instant());
                        break;
                    }
                    var payload = stateService.payload(attemptId);
                    if (payload == null) continue;
                    Instant beforeSend = analyticsClock.instant();
                    Duration ttl = Duration.between(beforeSend, payload.expiresAt());
                    PushProviderSendResult result;
                    PushProviderClient provider = providerClients.stream()
                            .filter(client -> client.provider() == pushProperties.getProvider()).findFirst().orElse(null);
                    if (ttl.isZero() || ttl.isNegative()) {
                        result = PushProviderSendResult.failed("TTL_EXPIRED");
                    } else if (provider == null) {
                        result = PushProviderSendResult.failed("PROVIDER_CLIENT_MISSING");
                    } else {
                        try {
                            result = provider.send(payload.token(), payload.notification(), ttl);
                        } catch (RuntimeException exception) {
                            result = PushProviderSendResult.uncertain(exception.getMessage());
                        }
                    }
                    stateService.recordProviderResult(attemptId, result, analyticsClock.instant());
                    attempted++;
                }
            } catch (RuntimeException exception) {
                log.warn("notification_outbox_failed outboxId={} reason={}", outboxId, exception.getMessage());
            }
        }
        return attempted;
    }

    private boolean deliveryAllowed() {
        return properties.isEnabled() && releaseGate.anyAudienceEnabled() && pushProperties.isEnabled()
                && subscriptionNotificationService.deliveryAllowed();
    }
}
