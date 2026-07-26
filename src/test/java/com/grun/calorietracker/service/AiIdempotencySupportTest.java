package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.service.support.AiIdempotencySupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiIdempotencySupportTest {

    @Test
    void normalizesSafeClientKey() {
        assertEquals("meal:request-123", AiIdempotencySupport.normalizeKey(" meal:request-123 "));
    }

    @Test
    void rejectsMissingOrUnsafeKeys() {
        assertThrows(IllegalArgumentException.class, () -> AiIdempotencySupport.normalizeKey(null));
        assertThrows(IllegalArgumentException.class, () -> AiIdempotencySupport.normalizeKey("short"));
        assertThrows(IllegalArgumentException.class, () -> AiIdempotencySupport.normalizeKey("invalid key with spaces"));
    }

    @Test
    void replaysCompletedRequest() {
        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setStatus(AiRequestStatus.DRAFT_CREATED);

        String result = AiIdempotencySupport.replayOrReject(history, ignored -> "cached", "AI meal-draft");

        assertEquals("cached", result);
    }

    @Test
    void blocksProcessingAndFailedKeys() {
        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setStatus(AiRequestStatus.PROCESSING);
        assertThrows(RequestConflictException.class,
                () -> AiIdempotencySupport.replayOrReject(history, ignored -> "cached", "AI meal-draft"));

        history.setStatus(AiRequestStatus.FAILED);
        assertThrows(RequestConflictException.class,
                () -> AiIdempotencySupport.replayOrReject(history, ignored -> "cached", "AI meal-draft"));
    }
}