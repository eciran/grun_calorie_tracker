package com.grun.calorietracker.service.evidence;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Provider-neutral contract for private product evidence stored through an
 * S3-compatible API. Returned URLs are bearer credentials and must never be logged.
 */
public interface FoodProductDirectUploadStorage {
    UploadAuthorization authorizeUpload(UploadObject object, Duration ttl);

    ReadAuthorization authorizeRead(String storageKey, Duration ttl);

    StoredObject inspect(String storageKey);

    byte[] readBounded(String storageKey, long maximumBytes);

    void delete(String storageKey);

    record UploadObject(
            String storageKey,
            String contentType,
            long sizeBytes,
            String sha256
    ) {
    }

    record UploadAuthorization(
            URI url,
            String method,
            Map<String, String> requiredHeaders,
            Instant expiresAt
    ) {
    }

    record ReadAuthorization(URI url, Instant expiresAt) {
    }

    record StoredObject(
            String storageKey,
            String contentType,
            long sizeBytes,
            String sha256
    ) {
    }
}
