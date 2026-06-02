package com.grun.calorietracker.service;

import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.service.impl.AiPhotoReferenceServiceImpl;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPhotoReferenceServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void createReference_storesFileAndReturnsPublicReference() {
        AiPhotoReferenceServiceImpl service = new AiPhotoReferenceServiceImpl(properties());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "meal.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        var result = service.createReference("user@example.com", file);

        assertTrue(result.getImageReference().startsWith("https://api.test/api/v1/ai/meal-drafts/photo-references/"));
        assertTrue(result.getStorageToken().endsWith(".jpg"));
        assertDoesNotThrow(() -> service.loadReference(result.getStorageToken()));
    }

    @Test
    void createReference_whenContentTypeNotAllowed_rejects() {
        AiPhotoReferenceServiceImpl service = new AiPhotoReferenceServiceImpl(properties());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "meal.gif",
                "image/gif",
                new byte[]{1, 2, 3}
        );

        assertThrows(IllegalArgumentException.class, () -> service.createReference("user@example.com", file));
    }

    @Test
    void loadReference_whenExpired_rejects() throws InterruptedException {
        AiProperties properties = properties();
        properties.getPhoto().setReferenceTtl(Duration.ofMillis(1));
        AiPhotoReferenceServiceImpl service = new AiPhotoReferenceServiceImpl(properties);
        MockMultipartFile file = new MockMultipartFile("file", "meal.jpg", "image/jpeg", new byte[]{1});

        var result = service.createReference("user@example.com", file);

        Thread.sleep(10);

        assertThrows(IllegalArgumentException.class, () -> service.loadReference(result.getStorageToken()));
    }

    @Test
    void cleanupExpiredReferences_removesExpiredFiles() throws InterruptedException {
        AiProperties properties = properties();
        properties.getPhoto().setReferenceTtl(Duration.ofMillis(1));
        AiPhotoReferenceServiceImpl service = new AiPhotoReferenceServiceImpl(properties);
        MockMultipartFile file = new MockMultipartFile("file", "meal.jpg", "image/jpeg", new byte[]{1});

        service.createReference("user@example.com", file);
        Thread.sleep(10);

        long deleted = service.cleanupExpiredReferences();

        assertTrue(deleted >= 1);
    }

    private AiProperties properties() {
        AiProperties properties = new AiProperties();
        properties.getPhoto().setStorageDirectory(tempDir.toString());
        properties.getPhoto().setPublicBaseUrl("https://api.test");
        properties.getPhoto().setMaxUploadBytes(1024);
        properties.getPhoto().setReferenceTtl(Duration.ofMinutes(30));
        return properties;
    }
}
