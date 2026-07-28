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

    @Test
    void key_separatesRangesComparisonFlagsUsersAndRevisions() {
        var firstUser = new UserAnalyticsCacheIdentity(42L, 7L, "Europe/Dublin");
        var secondUser = new UserAnalyticsCacheIdentity(43L, 7L, "Europe/Dublin");

        String base = factory.key(firstUser, "progress", "2026-07-01", "2026-07-07", false);

        assertFalse(base.equals(factory.key(firstUser, "progress", "2026-07-02", "2026-07-08", false)));
        assertFalse(base.equals(factory.key(firstUser, "progress", "2026-07-01", "2026-07-07", true)));
        assertFalse(base.equals(factory.key(secondUser, "progress", "2026-07-01", "2026-07-07", false)));
        assertFalse(base.equals(factory.key(new UserAnalyticsCacheIdentity(42L, 8L, "Europe/Dublin"),
                "progress", "2026-07-01", "2026-07-07", false)));
    }}
