package com.grun.calorietracker.service.reminder;

import com.grun.calorietracker.config.MealReminderDeliveryProperties;
import com.grun.calorietracker.config.PushProperties;
import com.grun.calorietracker.service.push.PushProviderClient;
import com.grun.calorietracker.service.push.PushProviderSendResult;
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
public class MealReminderOutboxDispatcher {
    private final MealReminderClaimService claimService;
    private final MealReminderDispatchStateService stateService;
    private final List<PushProviderClient> providerClients;
    private final MealReminderDeliveryProperties properties;
    private final PushProperties pushProperties;
    private final Clock analyticsClock;
    private final MealReminderPolicyFactory policyFactory;

    @Scheduled(fixedDelayString = "${grun.meal-reminders.outbox-scan-ms:5000}")
    public void dispatchScheduled() {
        dispatchOnce();
    }

    public int dispatchOnce() {
        if (!deliveryAllowed()) return 0;
        String workerId = "meal-outbox-" + UUID.randomUUID();
        int attempted = 0;
        for (Long outboxId : claimService.claimOutbox(workerId, analyticsClock.instant())) {
            try {
                var prepared = stateService.revalidateAndPrepare(outboxId, workerId, analyticsClock.instant());
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
                    if (ttl.isZero() || ttl.isNegative()) {
                        result = PushProviderSendResult.failed("TTL_EXPIRED");
                    } else {
                        PushProviderClient provider = providerClients.stream()
                                .filter(client -> client.provider() == pushProperties.getProvider())
                                .findFirst().orElse(null);
                        if (provider == null) {
                            result = PushProviderSendResult.failed("No client for configured push provider");
                        } else {
                            try {
                                result = provider.send(payload.token(), payload.notification(), ttl);
                            } catch (RuntimeException ex) {
                                // The request may have reached the provider. Never blind-retry this outcome.
                                result = PushProviderSendResult.uncertain(ex.getMessage());
                            }
                        }
                    }
                    stateService.recordProviderResult(attemptId, result, analyticsClock.instant());
                    attempted++;
                }
            } catch (RuntimeException ex) {
                log.warn("meal_reminder_outbox_failed outboxId={} reason={}", outboxId, ex.getMessage());
            }
        }
        return attempted;
    }

    private boolean deliveryAllowed() {
        MealReminderContract.Mode mode = policyFactory.current().mode();
        return properties.isDeliveryEnabled() && pushProperties.isEnabled()
                && (mode == MealReminderContract.Mode.PILOT || mode == MealReminderContract.Mode.LIVE);
    }
}
