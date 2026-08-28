package com.grun.calorietracker.service.model;

public record ProductNutritionOcrEvidence(
        byte[] bytes,
        String contentType,
        String sha256
) {
    public ProductNutritionOcrEvidence {
        bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
