package com.grun.calorietracker.service.support;

import com.grun.calorietracker.config.ProfileMediaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AvatarUploadInspectorTest {
    private final AvatarUploadInspector inspector = new AvatarUploadInspector(new ProfileMediaProperties());

    @Test
    void acceptsImageWhenDeclaredTypeMatchesSignature() {
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

        var result = inspector.inspect(new MockMultipartFile("file", "avatar.png", "image/png", png));

        assertEquals("image/png", result.contentType());
        assertEquals(".png", result.extension());
        assertEquals(64, result.sha256().length());
    }

    @Test
    void rejectsSpoofedImageContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", "not-an-image".getBytes());

        assertThrows(IllegalArgumentException.class, () -> inspector.inspect(file));
    }
}
