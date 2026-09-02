package com.grun.calorietracker.service.reminder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MealReminderScheduler {
    private final MealReminderClaimService claimService;
    private final MealReminderCandidateWorker candidateWorker;
    private final MealReminderPolicyFactory policyFactory;
    private final Clock analyticsClock;

    @Scheduled(fixedDelayString = "${grun.meal-reminders.scan-interval-ms:300000}")
    public void scan() {
        Instant scanTime = analyticsClock.instant();
        claimService.bootstrapMissingSchedules(scanTime);
        if (policyFactory.current().mode() == MealReminderContract.Mode.OFF) return;
        String workerId = "meal-candidate-" + UUID.randomUUID();
        for (Long userId : claimService.claimSchedules(workerId, scanTime)) {
            Instant started = analyticsClock.instant();
            String error = null;
            try {
                candidateWorker.evaluate(userId, started);
            } catch (RuntimeException ex) {
                error = safeError(ex);
                log.warn("meal_reminder_candidate_failed userId={} reason={}", userId, error);
            } finally {
                claimService.completeSchedule(userId, workerId, started, analyticsClock.instant(), error);
            }
        }
    }

    private String safeError(RuntimeException ex) {
        String value = ex.getClass().getSimpleName() + ":" + ex.getMessage();
        return value.length() <= 200 ? value : value.substring(0, 200);
    }
}
