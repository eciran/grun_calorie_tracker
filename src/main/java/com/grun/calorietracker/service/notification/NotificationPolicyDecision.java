package com.grun.calorietracker.service.notification;

import com.grun.calorietracker.enums.NotificationDeliveryChannel;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;

public record NotificationPolicyDecision(
        Set<NotificationDeliveryChannel> allowedChannels,
        Instant pushAvailableAt,
        String reasonCode
) {
    public NotificationPolicyDecision {
        allowedChannels = allowedChannels == null ? Set.of() : Set.copyOf(allowedChannels);
    }

    public boolean allows(NotificationDeliveryChannel channel) {
        return allowedChannels.contains(channel);
    }

    public boolean suppressed() {
        return allowedChannels.isEmpty();
    }

    @Override
    public Set<NotificationDeliveryChannel> allowedChannels() {
        return Collections.unmodifiableSet(allowedChannels);
    }
}
