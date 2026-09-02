package com.grun.calorietracker.service.reminder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public record MealReminderDecision(
        Candidate candidate,
        MealReminderContract.Slot slot,
        boolean shouldSend,
        MealReminderContract.Reason reason,
        MealReminderContract.KcalReason kcalReason,
        MealReminderContract.Message message,
        Map<String, String> safeParameters,
        MealReminderContract.Copy renderedCopy,
        String policyVersion,
        Instant evaluatedAt,
        LocalDate localDate,
        Instant eligibleAt,
        Instant expiresAt
) {
    public MealReminderDecision {
        safeParameters = Map.copyOf(safeParameters);
        if (shouldSend && (message == null || renderedCopy == null)) {
            throw new IllegalArgumentException("Send decisions require a message and rendered copy");
        }
        if (message != null && !message.allowedParameters().equals(safeParameters.keySet())) {
            throw new IllegalArgumentException("Decision parameters do not match the message contract");
        }
        if (message == null && !safeParameters.isEmpty()) {
            throw new IllegalArgumentException("Suppressed decisions cannot carry parameters");
        }
    }

    public enum Candidate {
        BREAKFAST,
        LUNCH,
        DINNER,
        DAILY_CATCHUP
    }
}
