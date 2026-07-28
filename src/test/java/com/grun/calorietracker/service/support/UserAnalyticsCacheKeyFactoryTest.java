package com.grun.calorietracker.service.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UserAnalyticsCacheKeyFactoryTest {

    private final UserAnalyticsCacheKeyFactory factory = new UserAnalyticsCacheKeyFactory();

    @Test
    void key_usesInternalIdentityRevisionAndSanitizedDimensions() {
        var identity = new UserAnalyticsCacheIdentity(42L, 7L, "Europe/Dublin");

        String key = factory.key(identity, "progress:advanced", "2026-07-01", null, "PLUS / PRO");

        assertEquals("42:7:progress_advanced:2026-07-01:-:PLUS___PRO", key);
        assertFalse(key.contains("@"));
    }
}
