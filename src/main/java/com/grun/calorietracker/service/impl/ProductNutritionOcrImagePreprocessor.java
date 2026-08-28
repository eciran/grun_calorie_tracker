package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.ProductNutritionOcrProperties;
import com.grun.calorietracker.exception.AiProviderException;
import com.grun.calorietracker.service.model.ProductNutritionOcrEvidence;
import com.grun.calorietracker.service.model.ProductNutritionOcrPreparedEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProductNutritionOcrImagePreprocessor {
    private static final double PADDING = 0.03;
    private final ProductNutritionOcrProperties properties;

    public ProductNutritionOcrPreparedEvidence prepare(
            ProductNutritionOcrEvidence evidence,
            List<Map<String, Object>> wordBoxes) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(evidence.bytes()));
            if (source == null) {
                throw new AiProviderException("Product nutrition OCR evidence is not a supported image.");
            }
            Bounds bounds = bounds(wordBoxes);
            if (bounds == null) {
                return new ProductNutritionOcrPreparedEvidence(evidence, wordBoxes);
            }
            int left = (int) Math.floor(Math.max(0, bounds.left - PADDING) * source.getWidth());
            int top = (int) Math.floor(Math.max(0, bounds.top - PADDING) * source.getHeight());
            int right = (int) Math.ceil(Math.min(1, bounds.right + PADDING) * source.getWidth());
            int bottom = (int) Math.ceil(Math.min(1, bounds.bottom + PADDING) * source.getHeight());
            if (right <= left || bottom <= top) {
                return new ProductNutritionOcrPreparedEvidence(evidence, wordBoxes);
            }
            BufferedImage crop = source.getSubimage(left, top, right - left, bottom - top);
            BufferedImage output = upscale(crop, targetLongEdge());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(output, "png", bytes)) {
                throw new AiProviderException("Product nutrition OCR crop could not be encoded.");
            }
            double cropX = (double) left / source.getWidth();
            double cropY = (double) top / source.getHeight();
            double cropWidth = (double) (right - left) / source.getWidth();
            double cropHeight = (double) (bottom - top) / source.getHeight();
            return new ProductNutritionOcrPreparedEvidence(
                    new ProductNutritionOcrEvidence(bytes.toByteArray(), "image/png", evidence.sha256()),
                    remap(wordBoxes, cropX, cropY, cropWidth, cropHeight));
        } catch (IOException exception) {
            throw new AiProviderException("Product nutrition OCR evidence could not be processed.");
        }
    }

    private int targetLongEdge() {
        return switch (properties.getMediaResolution()) {
            case "LOW" -> 1024;
            case "MEDIUM" -> 1536;
            default -> 2048;
        };
    }

    private BufferedImage upscale(BufferedImage image, int target) {
        int longEdge = Math.max(image.getWidth(), image.getHeight());
        if (longEdge >= target) return image;
        double scale = (double) target / longEdge;
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(image, 0, 0, width, height, null);
        graphics.dispose();
        return resized;
    }

    private Bounds bounds(List<Map<String, Object>> words) {
        double left = 1, top = 1, right = 0, bottom = 0;
        boolean found = false;
        for (Map<String, Object> word : words) {
            Box box = box(word.get("box"));
            if (box == null) continue;
            found = true;
            left = Math.min(left, box.x); top = Math.min(top, box.y);
            right = Math.max(right, box.x + box.width); bottom = Math.max(bottom, box.y + box.height);
        }
        return found ? new Bounds(left, top, right, bottom) : null;
    }

    private List<Map<String, Object>> remap(List<Map<String, Object>> words, double x, double y, double w, double h) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> word : words) {
            Map<String, Object> copy = new LinkedHashMap<>(word);
            Box box = box(word.get("box"));
            if (box != null) {
                copy.put("box", Map.of(
                        "x", clamp((box.x - x) / w), "y", clamp((box.y - y) / h),
                        "width", clamp(box.width / w), "height", clamp(box.height / h)));
            }
            result.add(Map.copyOf(copy));
        }
        return List.copyOf(result);
    }

    private Box box(Object value) {
        if (!(value instanceof Map<?, ?> map)) return null;
        Double x = number(map.get("x")), y = number(map.get("y"));
        Double width = number(map.get("width")), height = number(map.get("height"));
        if (x == null || y == null || width == null || height == null || width <= 0 || height <= 0
                || x < 0 || y < 0 || x + width > 1 || y + height > 1) return null;
        return new Box(x, y, width, height);
    }

    private Double number(Object value) {
        return value instanceof Number number && Double.isFinite(number.doubleValue()) ? number.doubleValue() : null;
    }

    private double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    private record Box(double x, double y, double width, double height) { }
    private record Bounds(double left, double top, double right, double bottom) { }
}
