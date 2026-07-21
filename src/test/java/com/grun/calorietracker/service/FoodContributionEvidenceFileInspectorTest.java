package com.grun.calorietracker.service;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import com.grun.calorietracker.service.support.FoodContributionEvidenceFileInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoodContributionEvidenceFileInspectorTest {

    private FoodContributionStorageProperties properties;
    private FoodContributionEvidenceFileInspector inspector;

    @BeforeEach
    void setUp() {
        properties = new FoodContributionStorageProperties();
        inspector = new FoodContributionEvidenceFileInspector(properties);
    }

    @Test
    void inspect_validJpegCalculatesTrustedMetadata() {
        byte[] bytes = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3};

        var result = inspector.inspect(new MockMultipartFile("file", "label.jpg", "image/jpeg", bytes));

        assertEquals("image/jpeg", result.contentType());
        assertEquals(".jpg", result.extension());
        assertEquals("6456b2ac4bae7c410724a1dbd8eeaaf2a9cb6b03c3e6c82ccd8101b284656791", result.checksum());
    }

    @Test
    void inspect_rejectsDeclaredContentTypeWhenSignatureDoesNotMatch() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.jpg", "image/jpeg", "not-an-image".getBytes());

        assertThrows(IllegalArgumentException.class, () -> inspector.inspect(file));
    }

    @Test
    void inspect_rejectsFilesOverConfiguredLimit() {
        properties.setMaxUploadBytes(3);
        MockMultipartFile file = new MockMultipartFile("file", "label.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1});

        assertThrows(IllegalArgumentException.class, () -> inspector.inspect(file));
    }
}
