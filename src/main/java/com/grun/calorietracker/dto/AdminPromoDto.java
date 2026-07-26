package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.*;

import java.time.LocalDateTime;

public record AdminPromoDto(
        Long id, Long version, String code, String name, String description,
        Double discountPercent, PromoStatus status, PromoType promoType, boolean active,
        LocalDateTime startAt, LocalDateTime endAt, SubscriptionPlan targetPlan,
        String targetProductId, PromoStore targetStore, MarketRegion targetRegion,
        String currency, String eligibilityRule, Integer perUserLimit, Integer globalLimit,
        Integer usedCount, String campaignKey, String providerOfferId, String providerProductId,
        boolean providerMappingReady, String createdBy, LocalDateTime createdAt,
        String updatedBy, LocalDateTime updatedAt, String deactivatedReason
) {}
