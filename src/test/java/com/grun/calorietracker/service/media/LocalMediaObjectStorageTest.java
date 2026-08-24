package com.grun.calorietracker.service.media;

import com.grun.calorietracker.config.MediaStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalMediaObjectStorageTest {

    @TempDir
    Path directory;

    @Test
    void storesInspectsReadsAndDeletesManagedMedia() {
        MediaStorageProperties properties = properties();
        MediaStorageKeyPolicy keyPolicy = new MediaStorageKeyPolicy(properties);
        LocalMediaObjectStorage storage = new LocalMediaObjectStorage(properties, keyPolicy);
        String key = keyPolicy.create(MediaNamespace.PRIVATE_PRODUCT_EVIDENCE, "u7", ".jpg");
        byte[] content = new byte[]{1, 2, 3, 4};

        storage.store(key, content, "image/jpeg", "abc123");

        MediaObjectStorage.StoredMediaObject inspected = storage.inspect(key);
        assertEquals(4, inspected.sizeBytes());
        assertEquals("abc123", inspected.sha256());
        assertArrayEquals(content, storage.readBounded(key, 4));
        assertThrows(IllegalArgumentException.class, () -> storage.readBounded(key, 3));

        storage.delete(key);
        assertThrows(IllegalArgumentException.class, () -> storage.inspect(key));
    }

    @Test
    void refusesKeysOutsideManagedNamespaces() {
        MediaStorageProperties properties = properties();
        LocalMediaObjectStorage storage = new LocalMediaObjectStorage(
                properties,
                new MediaStorageKeyPolicy(properties)
        );

        assertThrows(IllegalArgumentException.class,
                () -> storage.store("../outside.jpg", new byte[]{1}, "image/jpeg", "hash"));
    }

    private MediaStorageProperties properties() {
        MediaStorageProperties properties = new MediaStorageProperties();
        properties.setLocalDirectory(directory.toString());
        return properties;
    }
}
