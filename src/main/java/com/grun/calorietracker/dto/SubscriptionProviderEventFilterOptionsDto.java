package com.grun.calorietracker.dto;

import java.util.List;

public record SubscriptionProviderEventFilterOptionsDto(
        List<String> eventTypes,
        List<String> eventIds,
        List<String> productIds
) {
}
