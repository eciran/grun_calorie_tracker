package com.grun.calorietracker.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserEntityLifecycleTest {

    @Test
    void onCreate_setsRegistrationAndUpdateTimestampsForNewUser() {
        UserEntity user = new UserEntity();

        user.onCreate();

        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());
    }

    @Test
    void onUpdate_doesNotInventLegacyRegistrationTimestamp() {
        UserEntity legacyUser = new UserEntity();
        legacyUser.setCreatedAt(null);
        legacyUser.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        legacyUser.onUpdate();

        assertNull(legacyUser.getCreatedAt());
        assertNotNull(legacyUser.getUpdatedAt());
    }
}
