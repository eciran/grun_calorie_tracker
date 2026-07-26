package com.grun.calorietracker.dto;

import java.util.List;

public record AdminPromoMetricsDto(
        long activePromos, long totalRedemptions, long convertedRedemptions,
        long rejectedRedemptions, long uniqueUsers, List<CurrencyRevenue> revenueByCurrency,
        double conversionRate, double rejectionRate,
        long duplicateAttempts, long limitRejections, long abuseSignals
) {
    public record CurrencyRevenue(String currency, long amountMinor) {}
}
