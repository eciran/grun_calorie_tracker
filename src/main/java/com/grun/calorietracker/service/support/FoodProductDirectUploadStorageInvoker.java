package com.grun.calorietracker.service.support;

import com.grun.calorietracker.service.evidence.FoodProductDirectUploadStorage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/** Suspends database transactions while private object storage is contacted. */
@Component
public class FoodProductDirectUploadStorageInvoker {

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FoodProductDirectUploadStorage.UploadAuthorization authorizeUpload(
            FoodProductDirectUploadStorage storage,
            FoodProductDirectUploadStorage.UploadObject object,
            Duration ttl
    ) {
        return storage.authorizeUpload(object, ttl);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FoodProductDirectUploadStorage.StoredObject inspect(
            FoodProductDirectUploadStorage storage,
            String storageKey
    ) {
        return storage.inspect(storageKey);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public byte[] readBounded(
            FoodProductDirectUploadStorage storage,
            String storageKey,
            long maxBytes
    ) {
        return storage.readBounded(storageKey, maxBytes);
    }
}
