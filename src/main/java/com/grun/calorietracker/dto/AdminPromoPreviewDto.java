package com.grun.calorietracker.dto;

import java.util.List;

public record AdminPromoPreviewDto(
        Long promoId, long estimatedAudience, boolean providerMappingReady,
        boolean activationReady, List<String> validationIssues
) {}
