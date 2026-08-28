package com.grun.calorietracker.service;

import com.grun.calorietracker.config.ProductNutritionOcrProperties;
import com.grun.calorietracker.service.impl.ProductNutritionOcrImagePreprocessor;
import com.grun.calorietracker.service.model.ProductNutritionOcrEvidence;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductNutritionOcrImagePreprocessorTest {
    @Test
    void cropsAroundWordsUpscalesAndRemapsBoxes() throws Exception {
        ProductNutritionOcrProperties properties = new ProductNutritionOcrProperties();
        properties.setMediaResolution("LOW");
        BufferedImage source = new BufferedImage(1000, 800, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);
        var words = List.<Map<String, Object>>of(Map.of(
                "text", "Energy", "box", Map.of("x", 0.60, "y", 0.40, "width", 0.20, "height", 0.10)));

        var result = new ProductNutritionOcrImagePreprocessor(properties).prepare(
                new ProductNutritionOcrEvidence(bytes.toByteArray(), "image/png", "sha"), words);

        BufferedImage crop = ImageIO.read(new ByteArrayInputStream(result.evidence().bytes()));
        assertEquals(1024, Math.max(crop.getWidth(), crop.getHeight()));
        assertTrue(crop.getWidth() < crop.getHeight() * 3);
        Map<?, ?> remapped = (Map<?, ?>) result.wordBoxes().get(0).get("box");
        assertTrue(((Number) remapped.get("x")).doubleValue() < 0.2);
        assertTrue(((Number) remapped.get("width")).doubleValue() > 0.7);
    }
}
