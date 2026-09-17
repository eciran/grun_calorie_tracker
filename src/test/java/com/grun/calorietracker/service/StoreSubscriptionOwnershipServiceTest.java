package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.RevenueCatWebhookEventDto;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StoreSubscriptionOwnershipServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final StoreSubscriptionOwnershipService service = new StoreSubscriptionOwnershipService(jdbc);

    @Test void existingOwnerIsAcceptedWithoutReassignment() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(1L), eq("APP_STORE"), eq("SANDBOX"), eq("chain")))
                .thenReturn(true);
        assertDoesNotThrow(() -> service.assertOwner(1L, event()));
        verify(jdbc).update(contains("ON CONFLICT DO NOTHING"), eq("APP_STORE"), eq("SANDBOX"), eq("chain"), eq(1L), eq("evt"));
    }

    @Test void differentOwnerOrAmbiguousHistoryIsRejected() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(2L), eq("APP_STORE"), eq("SANDBOX"), eq("chain")))
                .thenReturn(false);
        var error = assertThrows(IllegalArgumentException.class, () -> service.assertOwner(2L, event()));
        assertTrue(error.getMessage().startsWith("SUBSCRIPTION_OWNERSHIP_CONFLICT"));
    }

    @Test void missingEnvironmentFailsBeforeClaiming() {
        var event = event(); event.setEnvironment(null);
        assertThrows(IllegalArgumentException.class, () -> service.assertOwner(1L, event));
        verifyNoInteractions(jdbc);
    }

    @Test void productionUsesSeparateScope() {
        var event = event(); event.setEnvironment("PRODUCTION");
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(1L), eq("APP_STORE"), eq("PRODUCTION"), eq("chain")))
                .thenReturn(true);
        service.assertOwner(1L, event);
        verify(jdbc).update(anyString(), eq("APP_STORE"), eq("PRODUCTION"), eq("chain"), eq(1L), eq("evt"));
    }

    private RevenueCatWebhookEventDto.Event event() {
        var event = new RevenueCatWebhookEventDto.Event();
        event.setId("evt"); event.setStore("APP_STORE"); event.setEnvironment("SANDBOX");
        event.setOriginalTransactionId("chain"); return event;
    }
}
