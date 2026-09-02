package com.grun.calorietracker.service.reminder;

import com.grun.calorietracker.entity.MealReminderOccurrenceEntity;
import com.grun.calorietracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MealReminderCandidateWorker {
    private final UserRepository userRepository;
    private final DailyMealReminderSnapshotService snapshotService;
    private final MealReminderRuntimeStateFactory runtimeStateFactory;
    private final MealReminderPolicyFactory policyFactory;
    private final MealReminderDecisionEngine decisionEngine;
    private final MealReminderDryRunService dryRunService;
    private final MealReminderReservationService reservationService;

    public void evaluate(Long userId, Instant now) {
        var user = userRepository.findById(userId).orElse(null);
        MealReminderPolicy policy = policyFactory.current();
        if (user == null || policy.mode() == MealReminderContract.Mode.OFF) return;
        DailyMealReminderSnapshot snapshot = snapshotService.buildBatch(List.of(user), now).get(0);
        MealReminderOccurrenceEntity context = new MealReminderOccurrenceEntity();
        context.setUser(user);
        context.setLocalDate(snapshot.localDate());
        MealReminderRuntimeState runtime = runtimeStateFactory.current(user, context, now);
        if (policy.mode() == MealReminderContract.Mode.DRY_RUN) {
            dryRunService.evaluateAndRecord("user:" + userId, snapshot, policy, runtime);
            return;
        }
        MealReminderDecision decision = decisionEngine.evaluate(snapshot, policy, runtime);
        reservationService.reserve(user, decision, now);
    }
}
