package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.PromoStore;

import java.util.List;

public record AdminPromoReconciliationDto(
        Long promoId, PromoStore store, boolean mappingReady,
        String providerOfferId, String providerProductId, List<String> issues,
        String entitlementGuardrail
) {}
