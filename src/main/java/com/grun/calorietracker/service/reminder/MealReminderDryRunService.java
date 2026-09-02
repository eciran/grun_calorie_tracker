package com.grun.calorietracker.service.reminder;

import com.grun.calorietracker.entity.MealReminderDryRunDecisionEntity;
import com.grun.calorietracker.repository.MealReminderDryRunDecisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MealReminderDryRunService {

    public static final Duration RETENTION = Duration.ofDays(7);
    private static final String SAFE_SUBJECT_PATTERN = "[A-Za-z0-9:_-]{1,64}";

    private final MealReminderDecisionEngine decisionEngine;
    private final MealReminderDryRunDecisionRepository dryRunRepository;

    /** Side-effect-free preview: no dry-run row, occurrence, notification, counter or provider call. */
    @Transactional(readOnly = true)
    public MealReminderDecision preview(
            DailyMealReminderSnapshot snapshot,
            MealReminderPolicy policy,
            MealReminderRuntimeState runtime
    ) {
        return decisionEngine.evaluate(snapshot, policy, runtime);
    }

    /** Records only a redacted, expiring DRY_RUN result. Delivery dependencies are intentionally absent. */
    @Transactional
    public MealReminderDecision evaluateAndRecord(
            String subjectRef,
            DailyMealReminderSnapshot snapshot,
            MealReminderPolicy policy,
            MealReminderRuntimeState runtime
    ) {
        if (policy.mode() != MealReminderContract.Mode.DRY_RUN) {
            throw new IllegalArgumentException("Only DRY_RUN policies can create simulation records");
        }
        if (subjectRef == null || !subjectRef.matches(SAFE_SUBJECT_PATTERN)) {
            throw new IllegalArgumentException("subjectRef must be a non-sensitive synthetic/user reference");
        }

        MealReminderDecision decision = decisionEngine.evaluate(snapshot, policy, runtime);
        dryRunRepository.deleteByExpiresAtLessThanEqual(decision.evaluatedAt());
        dryRunRepository.save(toEntity(subjectRef, decision));
        return decision;
    }

    private MealReminderDryRunDecisionEntity toEntity(String subjectRef, MealReminderDecision decision) {
        Objects.requireNonNull(decision, "decision");
        MealReminderDryRunDecisionEntity entity = new MealReminderDryRunDecisionEntity();
        entity.setSubjectRef(subjectRef);
        entity.setPolicyVersion(decision.policyVersion());
        entity.setEvaluatedAt(decision.evaluatedAt());
        entity.setExpiresAt(decision.evaluatedAt().plus(RETENTION));
        entity.setLocalDate(decision.localDate());
        entity.setCandidate(decision.candidate());
        entity.setSlot(decision.slot());
        entity.setShouldSend(decision.shouldSend());
        entity.setReason(decision.reason());
        entity.setKcalReason(decision.kcalReason());
        entity.setMessageVariant(decision.message());
        return entity;
    }
}
