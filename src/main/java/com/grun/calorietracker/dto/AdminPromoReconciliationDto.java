package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.PromoStore;

import java.time.LocalDateTime;
import java.util.List;

public record AdminPromoReconciliationDto(
        Long promoId, PromoStore store, boolean mappingReady,
        String providerOfferId, String providerProductId,
        long observedProviderEvents, LocalDateTime lastObservedAt,
        String providerRoute, List<String> issues,
        String entitlementGuardrail
) {}
