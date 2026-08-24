package com.grun.calorietracker.service.media;

import com.grun.calorietracker.config.MediaStorageProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaStorageKeyPolicyTest {

    private final MediaStorageKeyPolicy policy = new MediaStorageKeyPolicy(new MediaStorageProperties());

    @Test
    void createsKeysInsideRequestedNamespace() {
        String key = policy.create(MediaNamespace.PROFILE_AVATAR, "u42", ".JPG");

        assertTrue(key.startsWith("grun/profile/avatars/u42/"));
        assertTrue(key.endsWith(".jpg"));
        assertTrue(policy.requireManaged(key).equals(key));
    }

    @Test
    void rejectsTraversalAndUnknownNamespaces() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.requireManaged("grun/profile/avatars/../secret.jpg"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.requireManaged("grun/unknown/u42/image.jpg"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.create(MediaNamespace.PROFILE_AVATAR, "../u42", ".jpg"));
    }
}
