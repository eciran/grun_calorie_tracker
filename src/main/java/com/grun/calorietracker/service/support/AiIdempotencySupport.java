package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.exception.RequestConflictException;

import java.util.function.Function;

public final class AiIdempotencySupport {

    private AiIdempotencySupport() {
    }

    public static String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required.");
        }
        String key = value.trim();
        if (key.length() < 8 || key.length() > 100 || !key.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("Idempotency-Key must contain 8-100 safe characters.");
        }
        return key;
    }

    public static <T> T replayOrReject(
            AiRequestHistoryEntity history,
            Function<AiRequestHistoryEntity, T> completedReader,
            String requestLabel) {
        if (history == null) {
            return null;
        }
        if (history.getStatus() == AiRequestStatus.DRAFT_CREATED
                || history.getStatus() == AiRequestStatus.CONFIRMED
                || history.getStatus() == AiRequestStatus.REJECTED) {
            return completedReader.apply(history);
        }
        if (history.getStatus() == AiRequestStatus.PROCESSING) {
            throw new RequestConflictException(
                    "An " + requestLabel + " request with this key is already processing.");
        }
        throw new RequestConflictException(
                "This idempotency key was already used by a failed " + requestLabel + " request. Use a new key.");
    }
}