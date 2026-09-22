package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ClientErrorTelemetryRequestDto;
import com.grun.calorietracker.dto.ProxyErrorTelemetryRequestDto;
import com.grun.calorietracker.service.OwnerErrorRecorder;
import com.grun.calorietracker.service.support.OwnerErrorEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.userdetails.User;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ErrorTelemetryControllerTest {
    private static final String PROXY_KEY = "fixture-proxy-key-with-24-chars";

    @Test
    void acceptsOnlySafeClientMetadataWithoutPersistingThePrincipal() {
        var recorder = mock(OwnerErrorRecorder.class);
        var controller = new ErrorTelemetryController(recorder, PROXY_KEY);
        var principal = User.withUsername("owner@example.com").password("ignored").roles("OWNER").build();
        var request = new ClientErrorTelemetryRequestDto("ADMIN_WEB", "TIMEOUT", "GET", "/api/v1/users/{id}",
                UUID.randomUUID().toString(), "Browser (Windows)", "admin ui/v2", Instant.now(), 1500);

        assertEquals(202, controller.client(request, principal).getStatusCode().value());
        var event = ArgumentCaptor.forClass(OwnerErrorEvent.class);
        verify(recorder).record(event.capture());
        assertNull(event.getValue().status());
        assertEquals("ADMIN_WEB", event.getValue().source());
        assertEquals("CLIENT_TIMEOUT", event.getValue().errorCode());
        assertEquals("Browser__Windows_", event.getValue().clientPlatform());
        assertEquals("admin_ui_v2", event.getValue().appVersion());
        assertFalse(event.getValue().toString().contains("owner@example.com"));
    }

    @Test
    void rejectsUnsafeClientRoutesAndUsesARealRateLimitResponse() {
        var recorder = mock(OwnerErrorRecorder.class);
        var controller = new ErrorTelemetryController(recorder, PROXY_KEY);
        var principal = User.withUsername("owner@example.com").password("ignored").roles("OWNER").build();
        var unsafe = new ClientErrorTelemetryRequestDto("ADMIN_WEB", "NETWORK", "GET", "/api/v1/users?email=secret@example.com",
                null, "BROWSER", "v2", Instant.now(), 20);
        assertEquals(400, controller.client(unsafe, principal).getStatusCode().value());
        for (int i = 0; i < 29; i++) {
            var valid = new ClientErrorTelemetryRequestDto("ADMIN_WEB", "NETWORK", "GET", "/api/v1/users/{id}",
                    null, "BROWSER", "v2", Instant.now(), 20);
            assertEquals(202, controller.client(valid, principal).getStatusCode().value());
        }
        var limited = new ClientErrorTelemetryRequestDto("ADMIN_WEB", "NETWORK", "GET", "/api/v1/users/{id}",
                null, "BROWSER", "v2", Instant.now(), 20);
        assertEquals(429, controller.client(limited, principal).getStatusCode().value());
    }

    @Test
    void proxyIngestionRequiresSharedSecretAndKeepsTheIdempotencyKey() {
        var recorder = mock(OwnerErrorRecorder.class);
        var controller = new ErrorTelemetryController(recorder, PROXY_KEY);
        var eventKey = UUID.randomUUID();
        var request = new ProxyErrorTelemetryRequestDto(eventKey, Instant.now(), 503, "POST", "/api/v1/products/{id}",
                UUID.randomUUID().toString(), 900);

        assertEquals(403, controller.proxy("wrong-key", request).getStatusCode().value());
        assertEquals(202, controller.proxy(PROXY_KEY, request).getStatusCode().value());
        var event = ArgumentCaptor.forClass(OwnerErrorEvent.class);
        verify(recorder).record(event.capture());
        assertEquals(eventKey, event.getValue().eventKey());
        assertEquals(503, event.getValue().status());
        assertEquals("PROXY", event.getValue().source());
        assertEquals("PROXY_UPSTREAM_FAILURE", event.getValue().errorCode());
    }

    @Test
    void acceptsAuthenticatedMobileNetworkObservationWithSanitizedVersionMetadata() {
        var recorder = mock(OwnerErrorRecorder.class);
        var controller = new ErrorTelemetryController(recorder, PROXY_KEY);
        var principal = User.withUsername("mobile-user@example.com").password("ignored").roles("USER").build();
        var request = new ClientErrorTelemetryRequestDto("MOBILE", "NETWORK", "POST", "/api/v1/ai/meal-drafts",
                UUID.randomUUID().toString(), "IOS 19.0", "2.8.0 (410)", Instant.now(), 2400);

        assertEquals(202, controller.client(request, principal).getStatusCode().value());
        var event = ArgumentCaptor.forClass(OwnerErrorEvent.class); verify(recorder).record(event.capture());
        assertEquals("MOBILE", event.getValue().source());
        assertEquals("IOS_19.0", event.getValue().clientPlatform());
        assertEquals("2.8.0__410_", event.getValue().appVersion());
        assertFalse(event.getValue().toString().contains("mobile-user@example.com"));
    }
}
