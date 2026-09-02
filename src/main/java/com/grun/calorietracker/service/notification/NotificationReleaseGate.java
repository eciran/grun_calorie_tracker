package com.grun.calorietracker.service.notification;

import com.grun.calorietracker.config.NotificationDeliveryProperties;
import com.grun.calorietracker.enums.NotificationReleaseStage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationReleaseGate {
    private final NotificationDeliveryProperties properties;

    public ReleaseDecision evaluate(Long userId) {
        if (!properties.isEnabled()) return denied("DEPLOYMENT_GATE_OFF");
        NotificationReleaseStage stage = properties.getStage() == null
                ? NotificationReleaseStage.OFF : properties.getStage();
        return switch (stage) {
            case OFF -> denied("RELEASE_STAGE_OFF");
            case DRY_RUN -> denied("DRY_RUN_NO_DELIVERY");
            case TEST_ACCOUNTS -> contains(properties.getTestUserIds(), userId)
                    ? allowed() : denied("OUTSIDE_TEST_COHORT");
            case PILOT -> contains(properties.getTestUserIds(), userId)
                    || contains(properties.getPilotUserIds(), userId)
                    ? allowed() : denied("OUTSIDE_PILOT_COHORT");
            case LIVE -> liveEligible(userId) ? allowed() : denied("OUTSIDE_LIVE_COHORT");
        };
    }

    public boolean anyAudienceEnabled() {
        if (!properties.isEnabled() || properties.getStage() == null) return false;
        return switch (properties.getStage()) {
            case OFF, DRY_RUN -> false;
            case TEST_ACCOUNTS -> valid(properties.getTestUserIds()).stream().findAny().isPresent();
            case PILOT -> valid(properties.getTestUserIds()).stream().findAny().isPresent()
                    || valid(properties.getPilotUserIds()).stream().findAny().isPresent();
            case LIVE -> validPercentage() > 0;
        };
    }

    public String audienceReason() {
        if (!properties.isEnabled()) return "DEPLOYMENT_GATE_OFF";
        if (properties.getStage() == null || properties.getStage() == NotificationReleaseStage.OFF)
            return "RELEASE_STAGE_OFF";
        return switch (properties.getStage()) {
            case DRY_RUN -> "DRY_RUN_NO_DELIVERY";
            case TEST_ACCOUNTS -> anyAudienceEnabled() ? "RELEASE_AUDIENCE_READY" : "TEST_COHORT_EMPTY";
            case PILOT -> anyAudienceEnabled() ? "RELEASE_AUDIENCE_READY" : "PILOT_COHORT_EMPTY";
            case LIVE -> anyAudienceEnabled() ? "RELEASE_AUDIENCE_READY" : "LIVE_PERCENTAGE_ZERO";
            case OFF -> "RELEASE_STAGE_OFF";
        };
    }

    public int testAccountCount() { return valid(properties.getTestUserIds()).size(); }
    public int pilotAccountCount() { return valid(properties.getPilotUserIds()).size(); }

    private boolean liveEligible(Long userId) {
        if (contains(properties.getTestUserIds(), userId) || contains(properties.getPilotUserIds(), userId)) return true;
        if (userId == null || userId <= 0) return false;
        return Math.floorMod(userId - 1, 100) < validPercentage();
    }

    private int validPercentage() {
        return Math.max(0, Math.min(100, properties.getLivePercentage()));
    }

    private boolean contains(Set<Long> values, Long userId) {
        return userId != null && userId > 0 && valid(values).contains(userId);
    }

    private Set<Long> valid(Set<Long> values) {
        return values == null ? Set.of() : values.stream()
                .filter(value -> value != null && value > 0).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private ReleaseDecision allowed() { return new ReleaseDecision(true, "RELEASE_ELIGIBLE"); }
    private ReleaseDecision denied(String reason) { return new ReleaseDecision(false, reason); }

    public record ReleaseDecision(boolean allowed, String reasonCode) { }
}
