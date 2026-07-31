package com.grun.calorietracker.service.media;

public interface MediaObjectStorage {
    StoredMediaObject store(String storageKey, byte[] content, String contentType, String sha256);
    StoredMediaObject inspect(String storageKey);
    byte[] readBounded(String storageKey, long maximumBytes);
    void delete(String storageKey);

    record StoredMediaObject(String storageKey, String contentType, long sizeBytes, String sha256) {
    }
}
