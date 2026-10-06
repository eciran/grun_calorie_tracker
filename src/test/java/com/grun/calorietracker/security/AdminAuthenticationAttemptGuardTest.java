package com.grun.calorietracker.security;

import com.grun.calorietracker.exception.AdminAuthenticationRateLimitException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminAuthenticationAttemptGuardTest {
    @Test void caseVariantsShareBudgetAndOtherAccountsRemainIndependent() {
        var guard = new AdminAuthenticationAttemptGuard(new InMemoryRateLimiter());
        for (int i = 0; i < 5; i++) guard.requireAllowed(" Owner@Example.com ");
        assertEquals(60, assertThrows(AdminAuthenticationRateLimitException.class,
                () -> guard.requireAllowed("owner@example.com")).getRetryAfterSeconds());
        assertDoesNotThrow(() -> guard.requireAllowed("other@example.com"));
    }

    @Test void blockedAccountRecoversWithoutPermanentLock() {
        var clock = new MutableClock();
        var guard = new AdminAuthenticationAttemptGuard(new InMemoryRateLimiter(clock));
        for (int i = 0; i < 5; i++) guard.requireAllowed("owner@example.com");
        assertThrows(AdminAuthenticationRateLimitException.class, () -> guard.requireAllowed("owner@example.com"));
        clock.millis += 60_000;
        assertDoesNotThrow(() -> guard.requireAllowed("owner@example.com"));
    }

    @Test void sustainedAttemptsRequireLongerWaitThenRecover() {
        var clock = new MutableClock();
        var guard = new AdminAuthenticationAttemptGuard(new InMemoryRateLimiter(clock));
        for (int minute = 0; minute < 4; minute++) {
            for (int i = 0; i < 5; i++) guard.requireAllowed("owner@example.com");
            clock.millis += 60_000;
        }
        assertEquals(900, assertThrows(AdminAuthenticationRateLimitException.class,
                () -> guard.requireAllowed("owner@example.com")).getRetryAfterSeconds());
        clock.millis = 900_000;
        assertDoesNotThrow(() -> guard.requireAllowed("owner@example.com"));
    }

    @Test void limiterKeysDoNotContainEmail() {
        var limiter = mock(RequestRateLimiter.class);
        when(limiter.isAllowed(anyString(), anyInt(), anyLong())).thenReturn(true);
        new AdminAuthenticationAttemptGuard(limiter).requireAllowed("owner@example.com");
        verify(limiter).isAllowed(matches("admin-auth:account:15m:[a-f0-9]{64}"), eq(20), eq(900_000L));
        verify(limiter).isAllowed(matches("admin-auth:account:1m:[a-f0-9]{64}"), eq(5), eq(60_000L));
    }

    @Test void disabledRateLimitDoesNotConsultBackend() {
        var limiter = mock(RequestRateLimiter.class);
        var guard = new AdminAuthenticationAttemptGuard(limiter);
        ReflectionTestUtils.setField(guard, "enabled", false);
        guard.requireAllowed("owner@example.com");
        verifyNoInteractions(limiter);
    }

    @Test void redisOutageStillEnforcesLocalAccountBudget() {
        var provider = mock(org.springframework.beans.factory.ObjectProvider.class);
        var redis = mock(RedisRateLimiter.class);
        when(provider.getIfAvailable()).thenReturn(redis);
        when(redis.isAllowed(anyString(), anyInt(), anyLong())).thenThrow(new IllegalStateException("Redis unavailable"));
        @SuppressWarnings("unchecked")
        var limiter = new DelegatingRateLimiter(new InMemoryRateLimiter(), provider);
        var guard = new AdminAuthenticationAttemptGuard(limiter);
        for (int i = 0; i < 5; i++) guard.requireAllowed("owner@example.com");
        assertThrows(AdminAuthenticationRateLimitException.class, () -> guard.requireAllowed("owner@example.com"));
    }

    private static class MutableClock extends Clock {
        long millis;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }
}
