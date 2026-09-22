package com.grun.calorietracker.entity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiRequestHistoryEntityTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void captureCorrelationId_usesCurrentRequestContextWhenMissing() {
        MDC.put("correlationId", "request-correlation");
        AiRequestHistoryEntity history = new AiRequestHistoryEntity();

        history.captureCorrelationId();

        assertEquals("request-correlation", history.getCorrelationId());
    }

    @Test
    void captureCorrelationId_preservesExplicitValue() {
        MDC.put("correlationId", "request-correlation");
        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setCorrelationId("explicit-correlation");

        history.captureCorrelationId();

        assertEquals("explicit-correlation", history.getCorrelationId());
    }
}
